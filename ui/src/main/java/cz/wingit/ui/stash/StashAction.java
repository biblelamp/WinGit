package cz.wingit.ui.stash;

import java.awt.Cursor;
import java.awt.event.ActionEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutionException;

import javax.swing.AbstractAction;
import javax.swing.KeyStroke;
import javax.swing.SwingWorker;

import cz.wingit.core.GitService;
import cz.wingit.core.status.ChangeType;
import cz.wingit.core.status.FileChange;
import cz.wingit.ui.Messages;
import cz.wingit.ui.common.Dialogs;
import cz.wingit.ui.repositories.RepositoriesPane;

/**
 * "Stash Changes": puts all local changes of the opened repository aside, after asking for a
 * description and whether new files go along. Enabled while a repository is open.
 */
public final class StashAction extends AbstractAction {

    private static final long serialVersionUID = 1L;

    private final transient GitService git;
    private final RepositoriesPane pane;
    private final transient Runnable afterwards;
    private boolean busy;

    /** @param afterwards runs on the EDT after stashing, e.g. to refresh the panes */
    public StashAction(GitService git, RepositoriesPane pane, Runnable afterwards) {
        super(Messages.get("menu.local.stash"));
        putValue(MNEMONIC_KEY, KeyEvent.VK_H);
        putValue(ACCELERATOR_KEY, KeyStroke.getKeyStroke(KeyEvent.VK_S,
                InputEvent.CTRL_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK));
        this.git = git;
        this.pane = pane;
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
        busy = true;
        new SwingWorker<List<FileChange>, Void>() {
            @Override
            protected List<FileChange> doInBackground() {
                return git.status(repo).changes();
            }

            @Override
            protected void done() {
                busy = false;
                try {
                    List<FileChange> changes = get();
                    if (pane.openedRepository().equals(Optional.of(repo))) {
                        ask(repo, changes);
                    }
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                } catch (ExecutionException ex) {
                    Dialogs.showError(pane, Messages.get("stash.error.title"), ex);
                }
            }
        }.execute();
    }

    private void ask(Path repo, List<FileChange> changes) {
        if (changes.isEmpty()) {
            Dialogs.showInfo(pane, Messages.get("stash.title"), Messages.get("stash.nothing"));
            return;
        }
        if (changes.stream().anyMatch(c -> c.staged() == ChangeType.CONFLICTED || c.unstaged() == ChangeType.CONFLICTED)) {
            Dialogs.showInfo(pane, Messages.get("stash.title"), Messages.get("stash.conflicts"));
            return;
        }
        int untracked = (int) changes.stream().filter(c -> c.unstaged() == ChangeType.UNTRACKED).count();
        StashDialog.show(pane, changes.size(), untracked).ifPresent(choice -> stash(repo, choice));
    }

    private void stash(Path repo, StashDialog.Result choice) {
        busy = true;
        pane.setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
        new SwingWorker<Boolean, Void>() {
            @Override
            protected Boolean doInBackground() {
                return git.stash(repo, choice.message(), choice.includeUntracked());
            }

            @Override
            protected void done() {
                busy = false;
                pane.setCursor(null);
                try {
                    if (!get()) {
                        Dialogs.showInfo(pane, Messages.get("stash.title"), Messages.get("stash.onlyUntracked"));
                    }
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                } catch (ExecutionException ex) {
                    Dialogs.showError(pane, Messages.get("stash.error.title"), ex);
                }
                afterwards.run();
            }
        }.execute();
    }
}
