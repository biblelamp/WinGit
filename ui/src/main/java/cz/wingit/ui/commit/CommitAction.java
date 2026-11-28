package cz.wingit.ui.commit;

import java.awt.Component;
import java.awt.event.ActionEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Supplier;

import javax.swing.AbstractAction;
import javax.swing.KeyStroke;
import javax.swing.SwingWorker;

import cz.wingit.core.AmendCheck;
import cz.wingit.core.GitIdentity;
import cz.wingit.core.GitService;
import cz.wingit.core.status.FileChange;
import cz.wingit.ui.Messages;
import cz.wingit.ui.common.Dialogs;
import cz.wingit.ui.remote.RemoteOperation;
import cz.wingit.ui.repositories.RepositoriesPane;

/**
 * "Commit": reads the current changes, shows the commit dialog and, if asked to, pushes afterwards.
 * Files selected in the Files pane are pre-checked; with no selection all files are. A message written
 * but not committed is kept as a draft of the repository and offered again next time.
 */
public final class CommitAction extends AbstractAction {

    private static final long serialVersionUID = 1L;

    private final transient GitService git;
    private final RepositoriesPane pane;
    private final transient Supplier<List<FileChange>> selection;
    private final transient Runnable afterwards;
    private boolean busy;
    /** Opens the Repository Settings of a repository over the given window; {@code null} until installed. */
    private transient BiConsumer<Component, Path> openRepositorySettings;
    /** Saves and reads commit message drafts one after another, so a reopened dialog sees the last one. */
    private final transient ExecutorService drafts = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "wingit-commit-draft");
        thread.setDaemon(true);
        return thread;
    });

    /** How many previous messages the commit dialog offers. */
    private static final int RECENT_MESSAGES = 15;

    /** What the dialog needs, read together in the background. */
    private record DialogData(List<FileChange> changes, List<String> recentMessages, String mergeMessage,
                              List<String> tags, Instant savedDate, String draft, GitIdentity identity,
                              AmendCheck amend) {
    }

    /**
     * @param selection  files currently selected in the Files pane (called on the EDT)
     * @param afterwards runs on the EDT after a commit or push, e.g. to refresh the panes
     */
    public CommitAction(GitService git, RepositoriesPane pane, Supplier<List<FileChange>> selection,
                        Runnable afterwards) {
        super(Messages.get("menu.local.commit"));
        putValue(MNEMONIC_KEY, KeyEvent.VK_C);
        putValue(ACCELERATOR_KEY, KeyStroke.getKeyStroke(KeyEvent.VK_K, InputEvent.CTRL_DOWN_MASK));
        this.git = git;
        this.pane = pane;
        this.selection = selection;
        this.afterwards = afterwards;
        setEnabled(pane.openedRepository().isPresent());
        pane.addOpenListener(opened -> setEnabled(opened.isPresent()));
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        Optional<Path> opened = pane.openedRepository();
        if (busy || opened.isEmpty()) {
            return;
        }
        Path repo = opened.get();
        List<FileChange> preselected = selection.get();
        busy = true;
        new SwingWorker<DialogData, Void>() {
            @Override
            protected DialogData doInBackground() {
                List<FileChange> changes = git.status(repo).changes();
                AmendCheck amend;
                try {
                    amend = git.amendCheck(repo);
                } catch (RuntimeException e) {
                    amend = AmendCheck.notPossible(AmendCheck.State.NO_COMMIT); // a plain commit still works
                }
                // without changed files the dialog only opens to amend the message of the last commit
                boolean open = !changes.isEmpty() || amend.possible();
                List<String> messages;
                try {
                    messages = !open ? List.of() : git.recentMessages(repo, RECENT_MESSAGES);
                } catch (RuntimeException e) {
                    messages = List.of(); // only a convenience; the dialog works without it
                }
                String mergeMessage = null;
                try {
                    mergeMessage = !open ? null : git.mergeMessage(repo).orElse(null);
                } catch (RuntimeException e) {
                    // no prepared message; the user writes one
                }
                List<String> tags = List.of();
                try {
                    tags = !open ? List.of() : git.refs(repo).tags();
                } catch (RuntimeException e) {
                    // the tag name is then only checked by Git
                }
                Instant savedDate = !open ? null : git.savedCommitDate(repo).orElse(null);
                String draft = null;
                GitIdentity identity = null;
                if (open) {
                    try {
                        // through the draft queue: a draft saved a moment ago is written by now
                        draft = drafts.submit(() -> git.commitDraft(repo).orElse(null)).get();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } catch (ExecutionException e) {
                        // no draft
                    }
                    try {
                        identity = git.repositoryIdentity(repo).identity();
                    } catch (RuntimeException e) {
                        // shown as unknown
                    }
                }
                return new DialogData(changes, messages, mergeMessage, tags, savedDate, draft, identity, amend);
            }

            @Override
            protected void done() {
                busy = false;
                try {
                    DialogData data = get();
                    showDialog(repo, data, preselected);
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                } catch (ExecutionException ex) {
                    Dialogs.showError(pane, Messages.get("commit.error.title"), ex);
                }
            }
        }.execute();
    }

    private void showDialog(Path repo, DialogData data, List<FileChange> preselected) {
        if (!pane.openedRepository().equals(Optional.of(repo))) {
            return; // another repository was opened meanwhile
        }
        if (data.changes().isEmpty() && !data.amend().possible()) {
            Dialogs.showInfo(pane, Messages.get("commit.nothing.title"), Messages.get("commit.nothing.message"));
            return;
        }
        Optional<CommitDialog.Result> result = CommitDialog.show(pane, git, repo, data.changes(), preselected,
                data.recentMessages(), data.draft(), data.mergeMessage(), data.tags(), data.savedDate(), data.amend(),
                data.identity(), draft -> saveDraft(repo, draft),
                openRepositorySettings == null ? null : owner -> openRepositorySettings.accept(owner, repo));
        if (result.isEmpty()) {
            return;
        }
        afterwards.run();
        if (result.get() == CommitDialog.Result.COMMITTED_PUSH_REQUESTED) {
            new RemoteOperation(git, pane).run(RemoteOperation.Kind.PUSH, repo);
            afterwards.run();
        }
    }

    /** What a click on the Git user icon of the commit dialog opens: the Repository Settings over that dialog. */
    public void setOpenRepositorySettings(BiConsumer<Component, Path> openRepositorySettings) {
        this.openRepositorySettings = openRepositorySettings;
    }

    /** Keeps (or with {@code null}, removes) the draft in the background; a failure only loses the draft. */
    private void saveDraft(Path repo, String draft) {
        drafts.execute(() -> {
            try {
                git.setCommitDraft(repo, draft);
            } catch (RuntimeException e) {
                // only a convenience
            }
        });
    }
}
