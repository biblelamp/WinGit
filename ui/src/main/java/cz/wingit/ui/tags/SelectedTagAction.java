package cz.wingit.ui.tags;

import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutionException;

import javax.swing.AbstractAction;
import javax.swing.JCheckBox;
import javax.swing.SwingWorker;

import cz.wingit.core.GitService;
import cz.wingit.ui.Messages;
import cz.wingit.ui.branches.BranchesPane;
import cz.wingit.ui.common.Dialogs;
import cz.wingit.ui.common.RenameDialog;
import cz.wingit.ui.remote.RemoteOperation;

/**
 * "Push Tag", "Rename Tag" and "Delete Tag" for the tag selected in the Branches pane.
 * <ul>
 * <li>Rename can rename the tag on the server too; if that fails, the local rename is undone, so
 * both keep the same name.</li>
 * <li>Delete always asks first and can delete the tag on the server too (server first, so a failure
 * there keeps the local tag).</li>
 * </ul>
 */
public final class SelectedTagAction extends AbstractAction {

    private static final long serialVersionUID = 1L;

    private enum Kind {
        PUSH("pushTag"),
        RENAME("renameTag"),
        DELETE("deleteTag");

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
    public static SelectedTagAction push(GitService git, BranchesPane pane, Runnable afterwards) {
        return new SelectedTagAction(git, pane, afterwards, Kind.PUSH, KeyEvent.VK_P);
    }

    /** See {@link #push}. */
    public static SelectedTagAction rename(GitService git, BranchesPane pane, Runnable afterwards) {
        return new SelectedTagAction(git, pane, afterwards, Kind.RENAME, KeyEvent.VK_G);
    }

    /** See {@link #push}. */
    public static SelectedTagAction delete(GitService git, BranchesPane pane, Runnable afterwards) {
        return new SelectedTagAction(git, pane, afterwards, Kind.DELETE, KeyEvent.VK_L);
    }

    private SelectedTagAction(GitService git, BranchesPane pane, Runnable afterwards, Kind kind, int mnemonic) {
        super(Messages.get("menu.branch." + kind.key));
        putValue(MNEMONIC_KEY, mnemonic);
        this.git = git;
        this.pane = pane;
        this.afterwards = afterwards;
        this.kind = kind;
        updateEnabled();
        pane.addSelectionListener(this::updateEnabled);
    }

    private void updateEnabled() {
        setEnabled(!busy && pane.selectedTag().isPresent());
    }

    /** Read in the background before asking: where tags go, and the existing tags (for rename). */
    private record Prepared(String remote, List<String> tags) {
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
        new SwingWorker<Prepared, Void>() {
            @Override
            protected Prepared doInBackground() {
                return new Prepared(git.defaultRemote(repo).orElse(null),
                        kind == Kind.RENAME ? git.refs(repo).tags() : List.of());
            }

            @Override
            protected void done() {
                setBusy(false);
                try {
                    Prepared prepared = get();
                    switch (kind) {
                        case PUSH:
                            push(repo, tag, prepared.remote());
                            break;
                        case RENAME:
                            askRename(repo, tag, prepared);
                            break;
                        case DELETE:
                            confirmDelete(repo, tag, prepared.remote());
                            break;
                        default:
                            throw new IllegalStateException(kind.name());
                    }
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                } catch (ExecutionException ex) {
                    Dialogs.showError(pane, Messages.get(kind.key + ".title"), ex);
                }
            }
        }.execute();
    }

    private void push(Path repo, String tag, String remote) {
        if (remote == null) {
            Dialogs.showInfo(pane, Messages.get("pushTag.title"), Messages.get("pushTag.noRemote"));
            return;
        }
        setBusy(true);
        try {
            NewTagAction.pushTag(git, pane, repo, remote, tag);
        } finally {
            setBusy(false);
        }
        afterwards.run();
    }

    private void askRename(Path repo, String tag, Prepared prepared) {
        String remote = prepared.remote();
        RenameDialog.show(pane, Messages.get("renameTag.title"), tag, prepared.tags(), TagNameCheck::problem,
                        Messages.format("renameTag.header", tag), Messages.get("renameTag.description"),
                        remote == null ? null : Messages.format("renameTag.alsoRemote", remote))
                .ifPresent(r -> rename(repo, tag, r.newName(), r.option() ? remote : null));
    }

    /** Renames locally, then (if {@code remote} is given) on the server, undoing the local rename on failure. */
    private void rename(Path repo, String tag, String newName, String remote) {
        setBusy(true);
        new SwingWorker<Void, Void>() {
            @Override
            protected Void doInBackground() {
                git.renameTag(repo, tag, newName);
                return null;
            }

            @Override
            protected void done() {
                setBusy(false);
                try {
                    get();
                    if (remote != null && !renameOnServer(repo, tag, newName, remote)) {
                        undoRename(repo, tag, newName);
                        return;
                    }
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                } catch (ExecutionException ex) {
                    Dialogs.showError(pane, Messages.format("renameTag.error.title", tag), ex);
                }
                afterwards.run();
            }
        }.execute();
    }

    private boolean renameOnServer(Path repo, String tag, String newName, String remote) {
        setBusy(true);
        try {
            return new RemoteOperation(git, pane).run(
                    Messages.format("renameTag.remote.progress", tag, remote),
                    Messages.format("renameTag.remote.error.title", tag, remote),
                    repo,
                    (credentials, progress) -> git.renameRemoteTag(repo, remote, tag, newName, credentials, progress));
        } finally {
            setBusy(false);
        }
    }

    private void undoRename(Path repo, String tag, String newName) {
        setBusy(true);
        new SwingWorker<Void, Void>() {
            @Override
            protected Void doInBackground() {
                git.renameTag(repo, newName, tag);
                return null;
            }

            @Override
            protected void done() {
                setBusy(false);
                try {
                    get();
                    Dialogs.showInfo(pane, Messages.get("renameTag.title"),
                            Messages.format("renameTag.remote.rolledBack", tag));
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                } catch (ExecutionException ex) {
                    Dialogs.showError(pane, Messages.format("renameTag.error.title", newName), ex);
                }
                afterwards.run();
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
