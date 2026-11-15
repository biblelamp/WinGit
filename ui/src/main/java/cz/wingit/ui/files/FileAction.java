package cz.wingit.ui.files;

import java.awt.Desktop;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.function.Predicate;

import javax.swing.AbstractAction;
import javax.swing.SwingWorker;

import cz.wingit.core.GitException;
import cz.wingit.core.GitService;
import cz.wingit.core.status.ChangeType;
import cz.wingit.core.status.FileChange;
import cz.wingit.ui.Messages;
import cz.wingit.ui.common.Dialogs;

/**
 * Stage, Unstage, Discard and Delete for the files selected in the Files pane. Each is enabled only
 * when at least one selected file can take it; Discard and Delete ask first.
 */
public final class FileAction extends AbstractAction {

    private static final long serialVersionUID = 1L;

    private enum Kind {
        STAGE("stage", c -> c.unstaged() != null),
        UNSTAGE("unstage", c -> c.staged() != null),
        DISCARD("discard", c -> true),
        DELETE("delete", FileAction::existsInWorkingTree);

        final String key;
        final Predicate<FileChange> applies;

        Kind(String key, Predicate<FileChange> applies) {
            this.key = key;
            this.applies = applies;
        }
    }

    private final transient GitService git;
    private final FilesPane pane;
    private final Kind kind;
    private final transient Runnable afterwards;
    private boolean busy;

    /** @param afterwards runs on the EDT after the operation, e.g. to refresh the panes */
    public static FileAction stage(GitService git, FilesPane pane, Runnable afterwards) {
        return new FileAction(git, pane, Kind.STAGE, afterwards, KeyEvent.VK_S);
    }

    /** See {@link #stage}. */
    public static FileAction unstage(GitService git, FilesPane pane, Runnable afterwards) {
        return new FileAction(git, pane, Kind.UNSTAGE, afterwards, KeyEvent.VK_U);
    }

    /** See {@link #stage}. */
    public static FileAction discard(GitService git, FilesPane pane, Runnable afterwards) {
        return new FileAction(git, pane, Kind.DISCARD, afterwards, KeyEvent.VK_I);
    }

    /** See {@link #stage}. */
    public static FileAction delete(GitService git, FilesPane pane, Runnable afterwards) {
        return new FileAction(git, pane, Kind.DELETE, afterwards, KeyEvent.VK_D);
    }

    private FileAction(GitService git, FilesPane pane, Kind kind, Runnable afterwards, int mnemonic) {
        super(Messages.get("menu.local." + kind.key));
        putValue(MNEMONIC_KEY, mnemonic);
        this.git = git;
        this.pane = pane;
        this.kind = kind;
        this.afterwards = afterwards;
        updateEnabled();
        pane.addSelectionChangeListener(this::updateEnabled);
    }

    private void updateEnabled() {
        setEnabled(!busy && !applicable(pane.selectedChanges()).isEmpty());
    }

    private List<FileChange> applicable(List<FileChange> selected) {
        return selected.stream().filter(kind.applies).toList();
    }

    /** The file is on disk (it is not a deletion). */
    static boolean existsInWorkingTree(FileChange change) {
        return change.unstaged() != ChangeType.DELETED
                && !(change.staged() == ChangeType.DELETED && change.unstaged() == null);
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        Optional<Path> repo = pane.repository();
        List<FileChange> changes = applicable(pane.selectedChanges());
        if (busy || repo.isEmpty() || changes.isEmpty() || !confirm(changes)) {
            return;
        }
        busy = true;
        updateEnabled();
        new SwingWorker<Void, Void>() {
            @Override
            protected Void doInBackground() throws IOException {
                switch (kind) {
                    case STAGE:
                        git.stage(repo.get(), changes);
                        break;
                    case UNSTAGE:
                        git.unstage(repo.get(), changes);
                        break;
                    case DISCARD:
                        git.discard(repo.get(), changes);
                        break;
                    case DELETE:
                        deleteFiles(repo.get(), changes);
                        break;
                    default:
                        throw new IllegalStateException(kind.name());
                }
                return null;
            }

            @Override
            protected void done() {
                busy = false;
                updateEnabled();
                try {
                    get();
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                } catch (ExecutionException ex) {
                    Dialogs.showError(pane, Messages.get("files." + kind.key + ".error.title"), ex);
                }
                afterwards.run();
            }
        }.execute();
    }

    private boolean confirm(List<FileChange> changes) {
        if (kind == Kind.DISCARD) {
            return Dialogs.confirmWarning(pane, Messages.get("files.discard.title"),
                    Messages.format("files.discard.confirm", changes.size(), fileList(changes)));
        }
        if (kind == Kind.DELETE) {
            return Dialogs.confirmWarning(pane, Messages.get("files.delete.title"),
                    Messages.format(trashSupported() ? "files.delete.confirm.trash" : "files.delete.confirm.permanent",
                            changes.size(), fileList(changes)));
        }
        return true;
    }

    /** The paths of {@code changes} for a confirmation message (see {@link Dialogs#pathList}). */
    static String fileList(List<FileChange> changes) {
        return Dialogs.pathList(changes.stream().map(FileChange::path).toList());
    }

    private static boolean trashSupported() {
        return Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.MOVE_TO_TRASH);
    }

    /** Moves the files to the Recycle Bin, or deletes them where that is not available. */
    private static void deleteFiles(Path repo, List<FileChange> changes) throws IOException {
        boolean trash = trashSupported();
        for (FileChange change : changes) {
            Path file = repo.resolve(change.path());
            if (!Files.exists(file)) {
                continue;
            }
            if (trash) {
                if (!Desktop.getDesktop().moveToTrash(file.toFile())) {
                    throw new GitException(Messages.format("files.delete.error.trash", change.path()));
                }
            } else {
                Files.delete(file);
            }
        }
    }
}
