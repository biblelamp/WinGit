package cz.wingit.ui.branches;

import java.awt.Cursor;
import java.awt.event.ActionEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.function.Supplier;

import javax.swing.AbstractAction;
import javax.swing.JOptionPane;
import javax.swing.KeyStroke;
import javax.swing.SwingWorker;

import cz.wingit.core.GitService;
import cz.wingit.core.merge.MergeResult;
import cz.wingit.core.refs.LocalBranch;
import cz.wingit.core.refs.RemoteBranches;
import cz.wingit.core.refs.RepositoryRefs;
import cz.wingit.ui.Messages;
import cz.wingit.ui.common.Dialogs;
import cz.wingit.ui.repositories.RepositoriesPane;

/**
 * "Merge" and "Abort Merge" for the opened repository.
 * <p>
 * Merge lets the user pick a branch (the one selected in the Branches pane comes first), merges it
 * into the current branch and reports the result; on conflicts the user can resolve them (edit,
 * stage, commit) or abort right away. Abort Merge asks before throwing the merge away.
 */
public final class MergeAction extends AbstractAction {

    private static final long serialVersionUID = 1L;

    private final transient GitService git;
    private final RepositoriesPane repositories;
    private final transient Supplier<Optional<String>> preselection;
    private final transient Runnable afterwards;
    private final boolean abort;
    private boolean busy;

    /**
     * @param branches   pane whose selected branch is offered first
     * @param afterwards runs on the EDT after a merge or abort, e.g. to refresh the panes
     */
    public static MergeAction merge(GitService git, RepositoriesPane repositories, BranchesPane branches,
                                    Runnable afterwards) {
        MergeAction action = new MergeAction(git, repositories, () -> preselected(branches), afterwards, false,
                Messages.get("menu.branch.merge"));
        action.putValue(MNEMONIC_KEY, KeyEvent.VK_M);
        action.putValue(ACCELERATOR_KEY, KeyStroke.getKeyStroke(KeyEvent.VK_M,
                InputEvent.CTRL_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK));
        return action;
    }

    /** See {@link #merge}. */
    public static MergeAction abort(GitService git, RepositoriesPane repositories, Runnable afterwards) {
        MergeAction action = new MergeAction(git, repositories, Optional::empty, afterwards, true,
                Messages.get("menu.branch.abortMerge"));
        action.putValue(MNEMONIC_KEY, KeyEvent.VK_A);
        return action;
    }

    private MergeAction(GitService git, RepositoriesPane repositories, Supplier<Optional<String>> preselection,
                        Runnable afterwards, boolean abort, String name) {
        super(name);
        this.git = git;
        this.repositories = repositories;
        this.preselection = preselection;
        this.afterwards = afterwards;
        this.abort = abort;
        setEnabled(repositories.openedRepository().isPresent());
        repositories.addOpenListener(opened -> setEnabled(opened.isPresent()));
    }

    private static Optional<String> preselected(BranchesPane branches) {
        Optional<String> local = branches.selectedLocalBranch();
        if (local.isPresent()) {
            return local;
        }
        return branches.selectedRemoteBranch().map(t -> t.remote() + "/" + t.branch());
    }

    /** What the merge dialog needs, read in the background. */
    private record Prepared(boolean inProgress, String current, List<String> candidates) {
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        Optional<Path> opened = repositories.openedRepository();
        if (busy || opened.isEmpty()) {
            return;
        }
        Path repo = opened.get();
        Optional<String> preselected = preselection.get();
        busy = true;
        new SwingWorker<Prepared, Void>() {
            @Override
            protected Prepared doInBackground() {
                boolean inProgress = git.mergeInProgress(repo);
                if (abort || inProgress) {
                    return new Prepared(inProgress, null, List.of());
                }
                RepositoryRefs refs = git.refs(repo);
                return new Prepared(false, currentName(refs), candidates(refs));
            }

            @Override
            protected void done() {
                busy = false;
                try {
                    Prepared prepared = get();
                    if (repositories.openedRepository().equals(Optional.of(repo))) {
                        proceed(repo, prepared, preselected.orElse(null));
                    }
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                } catch (ExecutionException ex) {
                    Dialogs.showError(repositories, Messages.get("merge.error.title"), ex);
                }
            }
        }.execute();
    }

    private void proceed(Path repo, Prepared prepared, String preselected) {
        if (abort) {
            if (!prepared.inProgress()) {
                Dialogs.showInfo(repositories, Messages.get("merge.abort.title"), Messages.get("merge.abort.none"));
            } else if (Dialogs.confirmWarning(repositories, Messages.get("merge.abort.title"),
                    Messages.get("merge.abort.confirm"))) {
                runAbort(repo);
            }
            return;
        }
        if (prepared.inProgress()) {
            Dialogs.showInfo(repositories, Messages.get("merge.title"), Messages.get("merge.inProgress"));
            return;
        }
        if (prepared.candidates().isEmpty()) {
            Dialogs.showInfo(repositories, Messages.get("merge.title"), Messages.get("merge.noBranches"));
            return;
        }
        MergeDialog.show(repositories, prepared.current(), prepared.candidates(), preselected)
                .ifPresent(choice -> runMerge(repo, prepared.current(), choice));
    }

    /** Local branches except the current one, then remote branches ({@code remote/branch}). */
    static List<String> candidates(RepositoryRefs refs) {
        List<String> result = new ArrayList<>();
        for (LocalBranch branch : refs.localBranches()) {
            if (!branch.current()) {
                result.add(branch.name());
            }
        }
        for (RemoteBranches remote : refs.remotes()) {
            for (String branch : remote.branches()) {
                result.add(remote.remote() + "/" + branch);
            }
        }
        return result;
    }

    private static String currentName(RepositoryRefs refs) {
        return refs.localBranches().stream().filter(LocalBranch::current).map(LocalBranch::name).findFirst()
                .orElse(refs.detachedAt() != null ? refs.detachedAt() : "HEAD");
    }

    private void runMerge(Path repo, String current, MergeDialog.Result choice) {
        busy = true;
        repositories.setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
        new SwingWorker<MergeResult, Void>() {
            @Override
            protected MergeResult doInBackground() {
                return git.merge(repo, choice.ref(), choice.noFastForward());
            }

            @Override
            protected void done() {
                busy = false;
                repositories.setCursor(null);
                afterwards.run();
                try {
                    report(repo, current, choice.ref(), get());
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                } catch (ExecutionException ex) {
                    Dialogs.showError(repositories, Messages.format("merge.error.titleFor", choice.ref()), ex);
                }
            }
        }.execute();
    }

    private void report(Path repo, String current, String ref, MergeResult result) {
        switch (result.outcome()) {
            case UP_TO_DATE:
                Dialogs.showInfo(repositories, Messages.get("merge.title"),
                        Messages.format("merge.upToDate", current, ref));
                break;
            case CONFLICTS:
                showConflicts(repo, current, ref, result.conflicts());
                break;
            default:
                break; // the refreshed history shows the result
        }
    }

    private void showConflicts(Path repo, String current, String ref, List<String> conflicts) {
        String list = Dialogs.pathList(conflicts);
        String later = Messages.get("merge.conflicts.later");
        String abortNow = Messages.get("merge.conflicts.abort");
        int choice = JOptionPane.showOptionDialog(repositories,
                Messages.format("merge.conflicts.message", ref, current, conflicts.size(), list),
                Messages.get("merge.conflicts.title"), JOptionPane.DEFAULT_OPTION, JOptionPane.WARNING_MESSAGE,
                null, new Object[] {later, abortNow}, later);
        if (choice == 1) {
            runAbort(repo);
        }
    }

    private void runAbort(Path repo) {
        busy = true;
        new SwingWorker<Void, Void>() {
            @Override
            protected Void doInBackground() {
                git.abortMerge(repo);
                return null;
            }

            @Override
            protected void done() {
                busy = false;
                try {
                    get();
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                } catch (ExecutionException ex) {
                    Dialogs.showError(repositories, Messages.get("merge.abort.error.title"), ex);
                }
                afterwards.run();
            }
        }.execute();
    }
}
