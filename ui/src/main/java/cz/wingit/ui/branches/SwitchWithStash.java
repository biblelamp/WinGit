package cz.wingit.ui.branches;

import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.ExecutionException;

import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.SwingWorker;
import javax.swing.UIManager;

import com.formdev.flatlaf.util.UIScale;

import cz.wingit.core.GitCommandException;
import cz.wingit.core.GitService;
import cz.wingit.core.cli.LoggedCommand;
import cz.wingit.core.refs.StashedSwitch;
import cz.wingit.core.refs.SwitchClash;
import cz.wingit.core.status.ChangeType;
import cz.wingit.core.status.FileChange;
import cz.wingit.ui.Messages;
import cz.wingit.ui.common.CommandLogDialog;
import cz.wingit.ui.common.Dialogs;

/**
 * When Git refuses to switch branches while there are local changes, offers to stash them, switch and
 * bring them back. Nothing happens without the user's explicit choice; afterwards a log of all Git
 * commands (including the refused first attempt) is shown.
 */
final class SwitchWithStash {

    /** Most conflicted files listed by name; the rest are counted. */
    private static final int LISTED_CONFLICTS = 10;

    /**
     * A switch refused while there are local changes.
     *
     * @param failure   the refused switch
     * @param changed   number of changed files
     * @param untracked number of new (untracked) files among them
     * @param conflicts files with unresolved conflicts; if any, stashing cannot help (Git cannot stash them)
     * @param clashes   local changes predicted not to fit the target branch (empty list: all fit), or
     *                  empty if not predicted
     */
    record Refusal(GitCommandException failure, int changed, int untracked, List<String> conflicts,
                   Optional<List<SwitchClash>> clashes) {

        Refusal {
            conflicts = List.copyOf(conflicts);
            clashes = clashes.map(List::copyOf);
        }

        /** {@code true} if bringing the changes back on the target branch is predicted to end in conflicts. */
        boolean clashPredicted() {
            return clashes.map(list -> !list.isEmpty()).orElse(false);
        }
    }

    private SwitchWithStash() {
    }

    /**
     * Whether local changes may be in the way after {@code failure} (empty if there are none). Decided
     * from the repository, not from Git's (possibly translated) message. Without conflicts, also predicts
     * whether the changes would fit {@code branch}. Call off the EDT.
     *
     * @param remote {@code null} for a local branch, else the remote whose branch would get a local branch
     */
    static Optional<Refusal> check(GitService git, Path repo, GitCommandException failure, String branch,
                                   String remote) {
        List<FileChange> changes = git.status(repo).changes();
        if (changes.isEmpty()) {
            return Optional.empty();
        }
        List<String> conflicts = changes.stream()
                .filter(c -> c.staged() == ChangeType.CONFLICTED || c.unstaged() == ChangeType.CONFLICTED)
                .map(FileChange::path).toList();
        int untracked = (int) changes.stream().filter(c -> c.unstaged() == ChangeType.UNTRACKED).count();
        Optional<List<SwitchClash>> clashes = conflicts.isEmpty()
                ? git.predictSwitchClashes(repo, branch, remote) : Optional.empty();
        return Optional.of(new Refusal(failure, changes.size(), untracked, conflicts, clashes));
    }

    /**
     * Explains that the switch is blocked by unresolved conflicts ({@link Refusal#conflicts()}) and what
     * to do about them; Git's own message ("resolve your current index first") does not say which files.
     */
    static void explainConflicts(Component parent, String branch, Refusal refusal) {
        JOptionPane.showMessageDialog(parent, conflictsMessage(branch, refusal.conflicts()),
                Messages.format("branches.switch.error.title", branch), JOptionPane.ERROR_MESSAGE);
    }

    static String conflictsMessage(String branch, List<String> conflicts) {
        StringBuilder files = new StringBuilder();
        conflicts.stream().limit(LISTED_CONFLICTS).forEach(path -> files.append("    ").append(path).append('\n'));
        if (conflicts.size() > LISTED_CONFLICTS) {
            files.append("    ").append(Messages.format("switchStash.conflicted.more",
                    conflicts.size() - LISTED_CONFLICTS)).append('\n');
        }
        return Messages.format("switchStash.conflicted", branch, conflicts.size(), files.toString());
    }

    /**
     * Asks the user; if confirmed, runs the switch with stash in the background and shows its log.
     *
     * @param remote     {@code null} for a local branch, else the remote whose branch gets a new local branch
     * @param afterwards runs on the EDT at the end, whatever the user chose
     */
    static void offer(Component parent, GitService git, Path repo, String branch, String remote, Refusal refusal,
                      Runnable afterwards) {
        JCheckBox untrackedBox = refusal.untracked() == 0 ? null
                : new JCheckBox(Messages.format("switchStash.untracked", refusal.untracked()), true);
        if (untrackedBox != null) {
            untrackedBox.setToolTipText(Messages.get("switchStash.untracked.tooltip"));
        }
        List<Object> message = new ArrayList<>();
        message.add(Messages.format("switchStash.refused", branch));
        message.add(gitOutput(refusal.failure().stderr()));
        message.add(Messages.format("switchStash.offer", branch, refusal.changed()));
        if (refusal.clashPredicted()) {
            JLabel warning = new JLabel(Messages.format("switchStash.clash.header", branch));
            warning.setForeground(UIManager.getColor("Component.error.focusedBorderColor"));
            message.add(warning);
            message.add(textBox(clashList(branch, refusal.clashes().orElseThrow()), 70));
            message.add(Messages.format("switchStash.clash.advice", branch));
        } else if (refusal.clashes().isPresent()) {
            message.add(Messages.format("switchStash.fits", branch));
        }
        if (untrackedBox != null) {
            message.add(untrackedBox);
        }
        String restore = Messages.get("switchStash.proceed");
        String keep = Messages.get("switchStash.proceedKeep");
        String cancel = Messages.get("switchStash.cancel");
        // The safer choice comes first and is the default.
        Object[] options = refusal.clashPredicted() ? new Object[] {keep, restore, cancel}
                : new Object[] {restore, keep, cancel};
        int choice = JOptionPane.showOptionDialog(parent, message.toArray(),
                Messages.format("switchStash.title", branch), JOptionPane.DEFAULT_OPTION,
                refusal.clashPredicted() ? JOptionPane.WARNING_MESSAGE : JOptionPane.QUESTION_MESSAGE,
                null, options, options[0]);
        if (choice < 0 || options[choice] == cancel) {
            afterwards.run();
            return;
        }
        boolean bringBack = options[choice] == restore;
        boolean includeUntracked = untrackedBox != null && untrackedBox.isSelected();
        parent.setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
        new SwingWorker<StashedSwitch, Void>() {
            @Override
            protected StashedSwitch doInBackground() {
                return git.switchBranchWithStash(repo, branch, remote, includeUntracked, bringBack);
            }

            @Override
            protected void done() {
                parent.setCursor(null);
                afterwards.run();
                try {
                    report(parent, git, repo, branch, refusal, get(), afterwards);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } catch (ExecutionException e) {
                    Dialogs.showError(parent, Messages.format("branches.switch.error.title", branch), e.getCause());
                }
            }
        }.execute();
    }

    private static void report(Component parent, GitService git, Path repo, String branch, Refusal refusal,
                               StashedSwitch result, Runnable afterwards) {
        List<LoggedCommand> log = new ArrayList<>();
        GitCommandException failure = refusal.failure();
        log.add(new LoggedCommand(failure.command(), failure.exitCode(), failure.stderr().strip()));
        log.addAll(result.log());
        String summary = summary(branch, result);
        String goBack = null;
        if (result.canGoBack()) {
            summary += "\n" + Messages.format("switchStash.back.offer", result.previousBranch());
            goBack = Messages.format("switchStash.back", result.previousBranch());
        }
        boolean back = CommandLogDialog.show(parent, Messages.format("switchStash.title", branch),
                Messages.get("switchStash.log.header"), summary,
                result.outcome() != StashedSwitch.Outcome.RESTORED && result.outcome() != StashedSwitch.Outcome.STASHED,
                repo, log, goBack);
        if (back) {
            goBack(parent, git, repo, result, afterwards);
        }
    }

    /** Takes back a switch that ended in conflicts, in the background, and shows its log. */
    private static void goBack(Component parent, GitService git, Path repo, StashedSwitch conflicted,
                               Runnable afterwards) {
        String previous = conflicted.previousBranch();
        parent.setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
        new SwingWorker<StashedSwitch, Void>() {
            @Override
            protected StashedSwitch doInBackground() {
                return git.goBackFromStashedSwitch(repo, conflicted);
            }

            @Override
            protected void done() {
                parent.setCursor(null);
                afterwards.run();
                try {
                    StashedSwitch result = get();
                    boolean done = result.outcome() == StashedSwitch.Outcome.NOT_SWITCHED;
                    CommandLogDialog.show(parent, Messages.format("switchStash.back.title", previous),
                            Messages.get("switchStash.log.header"),
                            Messages.format(done ? "switchStash.back.done" : "switchStash.back.failed", previous),
                            !done, repo, result.log());
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } catch (ExecutionException e) {
                    Dialogs.showError(parent, Messages.format("switchStash.back.title", previous), e.getCause());
                }
            }
        }.execute();
    }

    /** What happened, in words, for the top of the log. */
    static String summary(String branch, StashedSwitch result) {
        String key = "switchStash.result." + result.outcome().name().toLowerCase(Locale.ROOT);
        return Messages.format(key, branch, result.conflicts().size());
    }

    /** The predicted clashes, one per line: the path and why it does not fit {@code branch}. */
    static String clashList(String branch, List<SwitchClash> clashes) {
        StringBuilder text = new StringBuilder();
        for (SwitchClash clash : clashes) {
            if (text.length() > 0) {
                text.append('\n');
            }
            String reason = "switchStash.clash." + clash.reason().name().toLowerCase(Locale.ROOT);
            text.append(Messages.format(reason, clash.path(), branch));
        }
        return text.toString();
    }

    /** Git's message in a small read-only box (it can list many files). */
    private static JScrollPane gitOutput(String stderr) {
        return textBox(stderr, 90);
    }

    /** Read-only text in a small scrollable box. */
    private static JScrollPane textBox(String text, int height) {
        JTextArea area = new JTextArea(text.strip());
        area.setEditable(false);
        area.setLineWrap(true);
        area.setWrapStyleWord(true);
        area.setCaretPosition(0);
        JScrollPane scroll = new JScrollPane(area);
        scroll.setPreferredSize(new Dimension(UIScale.scale(520), UIScale.scale(height)));
        return scroll;
    }
}
