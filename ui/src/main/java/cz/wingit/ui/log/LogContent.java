package cz.wingit.ui.log;

import java.awt.BorderLayout;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;

import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JSplitPane;

import cz.wingit.core.GitService;
import cz.wingit.core.log.CommitDetails;
import cz.wingit.core.log.CommitInfo;
import cz.wingit.ui.Messages;
import cz.wingit.ui.branches.BranchesPane;
import cz.wingit.ui.common.LatestLoader;
import cz.wingit.ui.common.SplitPanes;
import cz.wingit.ui.common.WindowLayout;
import cz.wingit.ui.diff.DiffPane;
import cz.wingit.ui.diff.DiffWindow;
import cz.wingit.ui.history.HistoryPane;

/**
 * Panes of the log window:
 * <pre>
 * +----------+--------------------+-----------+
 * | Branches | Commits            | Details   |
 * |          |                    +-----------+
 * |          |                    | Files     |
 * +----------+--------------------+-----------+
 * | Diff                                      |
 * +-------------------------------------------+
 * </pre>
 * Commits shows the whole history of HEAD. A double-click (or Enter) on a branch in Branches checks it out,
 * and Commits then shows the history of that branch at once.
 * Selecting a commit in Commits (the history pane) shows its details and changed files (read in the background); selecting
 * a file shows what the commit changed in it, compared with its first parent.
 */
final class LogContent extends JPanel {

    private static final long serialVersionUID = 1L;

    /** Split panes by the names used in saved layouts; keep the names stable. */
    private final transient SplitPanes splits = new SplitPanes();
    private final transient GitService git;
    private final BranchesPane branchesPane;
    private final HistoryPane historyPane;
    private final CommitDetailsPane detailsPane = new CommitDetailsPane();
    private final CommitFilesPane filesPane = new CommitFilesPane();
    private final DiffPane diffPane;
    private final transient LatestLoader<CommitDetails> loader;
    private transient Path repository;
    /** Commit whose details are shown or being read; {@code null} if none. */
    private String shownCommit;

    LogContent(GitService git) {
        super(new BorderLayout());
        this.git = git;
        branchesPane = new BranchesPane(git);
        historyPane = HistoryPane.fullHistory(git);
        historyPane.useCountedTitle("log.commits.title", "log.commits.loading", "log.commits.count", null);
        // the main window's file watcher may watch another repository: re-read the new HEAD right away
        branchesPane.addSwitchListener(historyPane::refresh);
        diffPane = new DiffPane(git);
        loader = new LatestLoader<>(this::showDetails, this::showError);
        historyPane.addSelectionListener(this::showCommit);
        filesPane.addOpenListener(file -> {
            if (shownCommit != null && repository != null) {
                DiffWindow.openCommitFile(this, git, repository, shownCommit, file);
            }
        });
        filesPane.addSelectionListener(file -> {
            if (shownCommit != null) {
                diffPane.showCommitFile(shownCommit, file);
            }
        });

        JSplitPane commit = split("commit", JSplitPane.VERTICAL_SPLIT, detailsPane, filesPane, 150, 0.0);
        JSplitPane center = split("center", JSplitPane.HORIZONTAL_SPLIT, historyPane, commit, 620, 0.6);
        JSplitPane top = split("top", JSplitPane.HORIZONTAL_SPLIT, branchesPane, center, 260, 0.0);
        add(split("main", JSplitPane.VERTICAL_SPLIT, top, diffPane, 420, 0.5), BorderLayout.CENTER);
    }

    /** Shows {@code workTree} in all panes. Call on the EDT. */
    void showRepository(Path workTree) {
        repository = workTree;
        branchesPane.showRepository(Optional.of(workTree));
        historyPane.showRepository(Optional.of(workTree));
        diffPane.showRepository(Optional.of(workTree));
        showCommit(Optional.empty());
    }

    /**
     * Re-reads branches and history without clearing them. The details of a commit never change, so
     * they are not read again. Call on the EDT.
     */
    void refresh() {
        branchesPane.refresh();
        historyPane.refresh();
    }

    /** Selects a commit in History, once it is read. Call on the EDT. */
    void selectCommit(String id) {
        historyPane.selectCommit(id);
    }

    /** Current dividers, keyed by split name. */
    Map<String, WindowLayout.Divider> dividers() {
        return splits.dividers();
    }

    /** Restores saved dividers (see {@link SplitPanes#restore}); call before the window is shown. */
    void restoreDividers(Map<String, WindowLayout.Divider> saved) {
        splits.restore(saved);
    }

    /** Split pane by name (for tests). */
    JSplitPane splitPane(String name) {
        return splits.get(name);
    }

    HistoryPane historyPane() {
        return historyPane;
    }

    BranchesPane branchesPane() {
        return branchesPane;
    }

    CommitDetailsPane detailsPane() {
        return detailsPane;
    }

    CommitFilesPane filesPane() {
        return filesPane;
    }

    private void showCommit(Optional<CommitInfo> commit) {
        String id = commit.map(CommitInfo::id).orElse(null);
        if (id != null && id.equals(shownCommit)) {
            return; // the history was re-read and kept its selection
        }
        shownCommit = id;
        diffPane.showCommitFile("", Optional.empty());
        if (id == null) {
            loader.cancel();
            detailsPane.showMessage(Messages.get("log.details.placeholder"));
            filesPane.showMessage(Messages.get("log.files.placeholder"));
            return;
        }
        detailsPane.showMessage(Messages.get("log.details.loading"));
        filesPane.showMessage(Messages.get("log.details.loading"));
        Path repo = repository;
        loader.load(() -> git.commitDetails(repo, id));
    }

    private void showDetails(CommitDetails details) {
        detailsPane.showCommit(details);
        filesPane.showFiles(details.files());
    }

    private void showError(Throwable error) {
        String message = Messages.format("log.details.error", error.getMessage());
        detailsPane.showMessage(message);
        filesPane.showMessage(message);
    }

    private JSplitPane split(String name, int orientation, JComponent first, JComponent second,
                             int dividerLocation, double resizeWeight) {
        return splits.create(name, orientation, first, second, dividerLocation, resizeWeight);
    }
}
