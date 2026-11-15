package cz.wingit.ui.main;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.GraphicsEnvironment;
import java.awt.Rectangle;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

import javax.swing.JFrame;
import javax.swing.Action;
import javax.swing.JPanel;
import javax.swing.WindowConstants;

import cz.wingit.core.GitService;
import cz.wingit.core.repo.RepositoryList;
import cz.wingit.ui.Messages;
import cz.wingit.ui.branches.BranchesPane;
import cz.wingit.ui.branches.DeleteBranchAction;
import cz.wingit.ui.branches.MergeAction;
import cz.wingit.ui.branches.NewBranchAction;
import cz.wingit.ui.branches.RenameBranchAction;
import cz.wingit.ui.commit.CommitAction;
import cz.wingit.ui.diff.DiffPane;
import cz.wingit.ui.files.FileAction;
import cz.wingit.ui.files.FilesPane;
import cz.wingit.ui.history.HistoryPane;
import cz.wingit.ui.remote.RemoteAction;
import cz.wingit.ui.repositories.AddRepositoryAction;
import cz.wingit.ui.repositories.CloneAction;
import cz.wingit.ui.repositories.CloseRepositoryAction;
import cz.wingit.ui.repositories.RemoveRepositoryAction;
import cz.wingit.ui.repositories.RepositoriesPane;

/**
 * Top-level application window: menu, toolbar, panes, status bar.
 */
public final class MainWindow extends JFrame {

    private static final long serialVersionUID = 1L;

    private final RepositoriesPane repositoriesPane;

    public MainWindow(GitService git, RepositoryList repositories) {
        super(Messages.get("mainWindow.title"));
        setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);

        repositoriesPane = new RepositoriesPane(git, repositories);
        RemoveRepositoryAction removeRepository = new RemoveRepositoryAction(repositoriesPane);
        repositoriesPane.installRemoveAction(removeRepository);
        FilesPane filesPane = new FilesPane(git);
        BranchesPane branchesPane = new BranchesPane(git);
        HistoryPane historyPane = new HistoryPane(git);
        DiffPane diffPane = new DiffPane(git);
        filesPane.addFileSelectionListener(diffPane::showFile);
        filesPane.addStatusListener((path, status) -> repositoriesPane.updateChanged(path, !status.isClean()));
        Runnable refresh = () -> {
            filesPane.refresh();
            branchesPane.refresh();
            historyPane.refresh();
            diffPane.refresh();
            repositoriesPane.openedRepository().ifPresent(repositoriesPane::refreshBranch);
        };
        AutoRefresh autoRefresh = new AutoRefresh(git, refresh);
        Action commit = new CommitAction(git, repositoriesPane, filesPane::selectedChanges, refresh);
        Action pull = RemoteAction.pull(git, repositoriesPane, refresh);
        Action push = RemoteAction.push(git, repositoriesPane, refresh);
        Action stage = FileAction.stage(git, filesPane, refresh);
        Action unstage = FileAction.unstage(git, filesPane, refresh);
        Action discard = FileAction.discard(git, filesPane, refresh);
        Action deleteFiles = FileAction.delete(git, filesPane, refresh);
        Action newBranch = new NewBranchAction(git, repositoriesPane, refresh);
        Action deleteBranch = new DeleteBranchAction(git, branchesPane, refresh);
        Action renameBranch = new RenameBranchAction(git, branchesPane, refresh);
        Action merge = MergeAction.merge(git, repositoriesPane, branchesPane, refresh);
        Action abortMerge = MergeAction.abort(git, repositoriesPane, refresh);
        branchesPane.installContextActions(renameBranch, deleteBranch, newBranch, merge);
        setJMenuBar(new MainMenuBar(
                new AddRepositoryAction(git, repositoriesPane),
                new CloneAction(git, repositoriesPane),
                new CloseRepositoryAction(repositoriesPane),
                removeRepository, commit, pull, push, newBranch, renameBranch, deleteBranch, merge, abortMerge,
                stage, unstage, discard, deleteFiles));
        filesPane.installContextActions(deleteFiles, stage, unstage, null, discard, deleteFiles);
        // Other repositories are not watched; re-check them when the user comes back to the window.
        addWindowFocusListener(new WindowAdapter() {
            @Override
            public void windowGainedFocus(WindowEvent e) {
                repositoriesPane.refreshAll();
            }
        });
        repositoriesPane.addOpenListener(opened -> {
            diffPane.showRepository(opened);
            filesPane.showRepository(opened);
            branchesPane.showRepository(opened);
            historyPane.showRepository(opened);
            autoRefresh.watch(opened);
        });
        setContentPane(createContent(Map.of("commit", commit, "merge", merge, "pull", pull, "push", push,
                        "stage", stage, "unstage", unstage, "discard", discard, "delete", deleteFiles),
                repositoriesPane, branchesPane, filesPane, diffPane, historyPane));

        setMinimumSize(new Dimension(640, 400));
        setSize(fitToScreen(1280, 820));
        setLocationRelativeTo(null);
    }

    /** Notified (on the EDT) when a repository is opened, or with empty when none is open any more. */
    public void addOpenListener(Consumer<Optional<Path>> listener) {
        repositoriesPane.addOpenListener(listener);
    }

    /**
     * Selects and opens a repository from the list, e.g. the one open in the last session.
     * Does nothing if it is no longer in the list. Call on the EDT.
     */
    public void reopen(Path workTree) {
        repositoriesPane.open(workTree);
    }

    static JPanel createContent(Map<String, Action> toolbarActions, RepositoriesPane repositoriesPane,
                                BranchesPane branchesPane, FilesPane filesPane, DiffPane diffPane,
                                HistoryPane historyPane) {
        JPanel root = new JPanel(new BorderLayout());
        root.add(new MainToolBar(toolbarActions), BorderLayout.NORTH);
        root.add(new MainContent(repositoriesPane, branchesPane, filesPane, diffPane, historyPane), BorderLayout.CENTER);
        root.add(new StatusBar(), BorderLayout.SOUTH);
        return root;
    }

    /** Preferred size, shrunk to 90 % of the usable screen area (taskbar excluded) if needed. */
    private static Dimension fitToScreen(int width, int height) {
        Rectangle usable = GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds();
        return new Dimension(
                Math.min(width, (int) (usable.width * 0.9)),
                Math.min(height, (int) (usable.height * 0.9)));
    }
}
