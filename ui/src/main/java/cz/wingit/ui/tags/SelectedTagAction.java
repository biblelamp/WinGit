package cz.wingit.ui.tags;

import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.ExecutionException;

import javax.swing.AbstractAction;
import javax.swing.JCheckBox;
import javax.swing.SwingWorker;

import cz.wingit.core.GitService;
import cz.wingit.ui.Messages;
import cz.wingit.ui.branches.BranchesPane;
import cz.wingit.ui.common.Dialogs;
import cz.wingit.ui.remote.RemoteOperation;

/**
 * "Push Tag" and "Delete Tag" for the tag selected in the Branches pane. Delete always asks first and
 * can delete the tag on the server too (server first, so a failure there keeps the local tag).
 */
public final class SelectedTagAction extends AbstractAction {

    private static final long serialVersionUID = 1L;

    private final transient GitService git;
    private final BranchesPane pane;
    private final transient Runnable afterwards;
    private final boolean delete;
    private boolean busy;

    /** @param afterwards runs on the EDT after the operation, e.g. to refresh the panes */
    public static SelectedTagAction push(GitService git, BranchesPane pane, Runnable afterwards) {
        SelectedTagAction action = new SelectedTagAction(git, pane, afterwards, false, Messages.get("menu.branch.pushTag"));
        action.putValue(MNEMONIC_KEY, KeyEvent.VK_P);
        return action;
    }

    /** See {@link #push}. */
    public static SelectedTagAction delete(GitService git, BranchesPane pane, Runnable afterwards) {
        SelectedTagAction action = new SelectedTagAction(git, pane, afterwards, true, Messages.get("menu.branch.deleteTag"));
        action.putValue(MNEMONIC_KEY, KeyEvent.VK_L);
        return action;
    }

    private SelectedTagAction(GitService git, BranchesPane pane, Runnable afterwards, boolean delete, String name) {
        super(name);
        this.git = git;
        this.pane = pane;
        this.afterwards = afterwards;
        this.delete = delete;
        updateEnabled();
        pane.addSelectionListener(this::updateEnabled);
    }

    private void updateEnabled() {
        setEnabled(!busy && pane.selectedTag().isPresent());
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        Optional<Path> opened = pane.repository();
        Optional<String> selected = pane.selectedTag();
        if (busy || opened.isEmpty() || selected.isEmpty()) {
            return;
        }
        Path repo = opened.get();
        String tag = selected.get();
        setBusy(true);
        new SwingWorker<Optional<String>, Void>() {
            @Override
            protected Optional<String> doInBackground() {
                return git.defaultRemote(repo);
            }

            @Override
            protected void done() {
                setBusy(false);
                try {
                    Optional<String> remote = get();
                    if (delete) {
                        confirmDelete(repo, tag, remote.orElse(null));
                    } else if (remote.isEmpty()) {
                        Dialogs.showInfo(pane, Messages.get("pushTag.title"), Messages.get("pushTag.noRemote"));
                    } else {
                        setBusy(true);
                        try {
                            NewTagAction.pushTag(git, pane, repo, remote.get(), tag);
                        } finally {
                            setBusy(false);
                        }
                        afterwards.run();
                    }
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                } catch (ExecutionException ex) {
                    Dialogs.showError(pane, Messages.get(delete ? "deleteTag.title" : "pushTag.title"), ex);
                }
            }
        }.execute();
    }

    private void confirmDelete(Path repo, String tag, String remote) {
        JCheckBox alsoRemote = remote == null ? null : new JCheckBox(Messages.format("deleteTag.alsoRemote", remote));
        String question = Messages.format("deleteTag.confirm", tag);
        Object message = alsoRemote == null ? question : new Object[] {question, alsoRemote};
        if (!Dialogs.confirmWarning(pane, Messages.get("deleteTag.title"), message)) {
            return;
        }
        if (alsoRemote != null && alsoRemote.isSelected()) {
            setBusy(true);
            boolean deleted;
            try {
                deleted = new RemoteOperation(git, pane).run(
                        Messages.format("deleteTag.remote.progress", tag, remote),
                        Messages.format("deleteTag.remote.error.title", tag, remote),
                        repo,
                        (credentials, progress) -> git.deleteRemoteTag(repo, remote, tag, credentials, progress));
            } finally {
                setBusy(false);
            }
            if (!deleted) {
                return; // keep the local tag if the server one could not be deleted
            }
        }
        deleteLocal(repo, tag);
    }

    private void deleteLocal(Path repo, String tag) {
        setBusy(true);
        new SwingWorker<Void, Void>() {
            @Override
            protected Void doInBackground() {
                git.deleteTag(repo, tag);
                return null;
            }

            @Override
            protected void done() {
                setBusy(false);
                try {
                    get();
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                } catch (ExecutionException ex) {
                    Dialogs.showError(pane, Messages.format("deleteTag.error.title", tag), ex);
                }
                afterwards.run();
            }
        }.execute();
    }

    private void setBusy(boolean busy) {
        this.busy = busy;
        updateEnabled();
    }
}
