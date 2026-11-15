package cz.wingit.ui.commit;

import java.awt.event.ActionEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.function.Supplier;

import javax.swing.AbstractAction;
import javax.swing.KeyStroke;
import javax.swing.SwingWorker;

import cz.wingit.core.GitService;
import cz.wingit.core.status.FileChange;
import cz.wingit.ui.Messages;
import cz.wingit.ui.common.Dialogs;
import cz.wingit.ui.remote.RemoteOperation;
import cz.wingit.ui.repositories.RepositoriesPane;

/**
 * "Commit": reads the current changes, shows the commit dialog and, if asked to, pushes afterwards.
 * Files selected in the Files pane are pre-checked; with no selection all files are.
 */
public final class CommitAction extends AbstractAction {

    private static final long serialVersionUID = 1L;

    private final transient GitService git;
    private final RepositoriesPane pane;
    private final transient Supplier<List<FileChange>> selection;
    private final transient Runnable afterwards;
    private boolean busy;

    /** How many previous messages the commit dialog offers. */
    private static final int RECENT_MESSAGES = 15;

    /** What the dialog needs, read together in the background. */
    private record DialogData(List<FileChange> changes, List<String> recentMessages) {
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
                List<String> messages;
                try {
                    messages = changes.isEmpty() ? List.of() : git.recentMessages(repo, RECENT_MESSAGES);
                } catch (RuntimeException e) {
                    messages = List.of(); // only a convenience; the dialog works without it
                }
                return new DialogData(changes, messages);
            }

            @Override
            protected void done() {
                busy = false;
                try {
                    DialogData data = get();
                    showDialog(repo, data.changes(), preselected, data.recentMessages());
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                } catch (ExecutionException ex) {
                    Dialogs.showError(pane, Messages.get("commit.error.title"), ex);
                }
            }
        }.execute();
    }

    private void showDialog(Path repo, List<FileChange> changes, List<FileChange> preselected,
                            List<String> recentMessages) {
        if (!pane.openedRepository().equals(Optional.of(repo))) {
            return; // another repository was opened meanwhile
        }
        if (changes.isEmpty()) {
            Dialogs.showInfo(pane, Messages.get("commit.nothing.title"), Messages.get("commit.nothing.message"));
            return;
        }
        Optional<CommitDialog.Result> result = CommitDialog.show(pane, git, repo, changes, preselected, recentMessages);
        if (result.isEmpty()) {
            return;
        }
        afterwards.run();
        if (result.get() == CommitDialog.Result.COMMITTED_PUSH_REQUESTED) {
            new RemoteOperation(git, pane).run(RemoteOperation.Kind.PUSH, repo);
            afterwards.run();
        }
    }
}
