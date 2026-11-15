package cz.wingit.ui.diff;

import java.nio.file.Path;
import java.util.Optional;

import cz.wingit.core.GitService;
import cz.wingit.core.diff.FileDiff;
import cz.wingit.core.status.FileChange;
import cz.wingit.ui.Messages;
import cz.wingit.ui.common.LatestLoader;
import cz.wingit.ui.common.TitledPane;

/**
 * Side-by-side diff of the file selected in the Files pane, read in the background.
 */
public final class DiffPane extends TitledPane {

    private static final long serialVersionUID = 1L;

    private final transient GitService git;
    private final DiffView view;
    private final transient LatestLoader<FileDiff> loader;
    private transient Path repository;
    private transient FileChange file;

    public DiffPane(GitService git) {
        this(git, new DiffView());
    }

    private DiffPane(GitService git, DiffView view) {
        super(Messages.get("diff.title"), view);
        this.git = git;
        this.view = view;
        this.loader = new LatestLoader<>(view::showDiff, this::showError);
        showRepository(Optional.empty());
    }

    /** Switches to another repository (or none); clears the shown file. Call on the EDT. */
    public void showRepository(Optional<Path> workTree) {
        repository = workTree.orElse(null);
        file = null;
        loader.cancel();
        view.showMessage(Messages.get(repository == null
                ? "diff.placeholder.noRepository" : "diff.placeholder.noFile"));
    }

    /** Shows the diff of a changed file of the current repository, or a hint for none. Call on the EDT. */
    public void showFile(Optional<FileChange> change) {
        if (repository == null) {
            return;
        }
        FileChange previous = file;
        file = change.orElse(null);
        if (file == null) {
            loader.cancel();
            view.showMessage(Messages.get("diff.placeholder.noFile"));
            return;
        }
        // same file with a new status (e.g. just staged): keep showing it until the new diff arrives
        if (previous == null || !previous.path().equals(file.path())) {
            view.showMessage(Messages.get("diff.placeholder.loading"));
        }
        Path repo = repository;
        FileChange selected = file;
        loader.load(() -> git.fileDiff(repo, selected));
    }

    /** Re-reads the shown file (its content may have changed on disk). Call on the EDT. */
    public void refresh() {
        loader.refresh();
    }

    /** For tests. */
    DiffView view() {
        return view;
    }

    private void showError(Throwable error) {
        view.showMessage(Messages.format("diff.placeholder.error", error.getMessage()));
    }
}
