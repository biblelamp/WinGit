package cz.wingit.ui.stash;

import java.awt.Cursor;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutionException;

import javax.swing.AbstractAction;
import javax.swing.SwingWorker;

import cz.wingit.core.GitService;
import cz.wingit.core.refs.Stash;
import cz.wingit.ui.Messages;
import cz.wingit.ui.branches.BranchesPane;
import cz.wingit.ui.common.Dialogs;

/**
 * "Apply Stash", "Apply and Delete Stash" and "Delete Stash" for the stash selected in the Branches
 * pane. Applying reports conflicts (the stash is kept then); deleting always asks first.
 */
public final class SelectedStashAction extends AbstractAction {

    private static final long serialVersionUID = 1L;

    private enum Kind {
        APPLY("applyStash"),
        POP("popStash"),
        DELETE("deleteStash");

        final String key;

        Kind(String key) {
            this.key = key;
        }
    }

    private final transient GitService git;
    private final BranchesPane pane;
    private final transient Runnable afterwards;
    private final Kind kind;
    private boolean busy;

    /** @param afterwards runs on the EDT after the operation, e.g. to refresh the panes */
    public static SelectedStashAction apply(GitService git, BranchesPane pane, Runnable afterwards) {
        return new SelectedStashAction(git, pane, afterwards, Kind.APPLY, KeyEvent.VK_A);
    }

    /** See {@link #apply}. */
    public static SelectedStashAction pop(GitService git, BranchesPane pane, Runnable afterwards) {
        return new SelectedStashAction(git, pane, afterwards, Kind.POP, KeyEvent.VK_P);
    }

    /** See {@link #apply}. */
    public static SelectedStashAction delete(GitService git, BranchesPane pane, Runnable afterwards) {
        return new SelectedStashAction(git, pane, afterwards, Kind.DELETE, KeyEvent.VK_E);
    }

    private SelectedStashAction(GitService git, BranchesPane pane, Runnable afterwards, Kind kind, int mnemonic) {
        super(Messages.get("menu.local." + kind.key));
        putValue(MNEMONIC_KEY, mnemonic);
        this.git = git;
        this.pane = pane;
        this.afterwards = afterwards;
        this.kind = kind;
        updateEnabled();
        pane.addSelectionListener(this::updateEnabled);
    }

    private void updateEnabled() {
        setEnabled(!busy && pane.selectedStash().isPresent());
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        Optional<Path> opened = pane.repository();
        Optional<Stash> selected = pane.selectedStash();
        if (busy || opened.isEmpty() || selected.isEmpty()) {
            return;
        }
        Stash stash = selected.get();
        if (kind == Kind.DELETE && !Dialogs.confirmWarning(pane, Messages.get("deleteStash.title"),
                Messages.format("deleteStash.confirm", stash.ref(), stash.message()))) {
            return;
        }
        run(opened.get(), stash);
    }

    private void run(Path repo, Stash stash) {
        setBusy(true);
        pane.setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
        new SwingWorker<List<String>, Void>() {
            @Override
            protected List<String> doInBackground() {
                if (kind == Kind.DELETE) {
                    git.dropStash(repo, stash);
                    return List.of();
                }
                return git.applyStash(repo, stash, kind == Kind.POP);
            }

            @Override
            protected void done() {
                setBusy(false);
                pane.setCursor(null);
                afterwards.run();
                try {
                    List<String> conflicts = get();
                    if (!conflicts.isEmpty()) {
                        Dialogs.showInfo(pane, Messages.get("applyStash.conflicts.title"),
                                Messages.format("applyStash.conflicts.message", stash.ref(), conflicts.size(),
                                        Dialogs.pathList(conflicts)));
                    }
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                } catch (ExecutionException ex) {
                    Dialogs.showError(pane, Messages.format(kind.key + ".error.title", stash.ref()), ex);
                }
            }
        }.execute();
    }

    private void setBusy(boolean busy) {
        this.busy = busy;
        updateEnabled();
    }
}
