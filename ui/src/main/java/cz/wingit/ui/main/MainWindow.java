package cz.wingit.ui.main;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Frame;
import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;
import java.awt.Rectangle;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

import javax.swing.JFrame;
import javax.swing.Action;
import javax.swing.JPanel;
import javax.swing.WindowConstants;

import com.formdev.flatlaf.extras.FlatSVGUtils;

import cz.wingit.core.GitService;
import cz.wingit.core.repo.RepositoryList;
import cz.wingit.core.status.FileChange;
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
import cz.wingit.ui.tags.NewTagAction;
import cz.wingit.ui.tags.SelectedTagAction;

/**
 * Top-level application window: menu, toolbar, panes, status bar.
 */
public final class MainWindow extends JFrame {

    private static final long serialVersionUID = 1L;

    /** How much of the saved window's title strip must be on some screen to restore its position. */
    private static final int MIN_VISIBLE_WIDTH = 100;
    private static final int MIN_VISIBLE_HEIGHT = 20;

    private final RepositoriesPane repositoriesPane;
    private final MainContent mainContent;
    private final FilesPane filesPane;
    private Optional<Path> openedRepository = Optional.empty();
    private String version;
    /** Bounds in the normal (not maximized) state; what we save and restore. */
    private Rectangle normalBounds;

    public MainWindow(GitService git, RepositoryList repositories) {
        super(Messages.get("mainWindow.title"));
        setIconImages(FlatSVGUtils.createWindowIconImages("/cz/wingit/ui/icons/app/wingit.svg"));
        setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);

        repositoriesPane = new RepositoriesPane(git, repositories);
        RemoveRepositoryAction removeRepository = new RemoveRepositoryAction(repositoriesPane);
        repositoriesPane.installRemoveAction(removeRepository);
        filesPane = new FilesPane(git);
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
        Action newTag = NewTagAction.onHead(git, repositoriesPane, refresh);
        Action pushTag = SelectedTagAction.push(git, branchesPane, refresh);
        Action renameTag = SelectedTagAction.rename(git, branchesPane, refresh);
        Action deleteTag = SelectedTagAction.delete(git, branchesPane, refresh);
        branchesPane.installTagActions(renameTag, deleteTag, newTag, pushTag);
        historyPane.installContextActions(
                NewTagAction.onCommit(git, repositoriesPane, historyPane::selectedCommit, refresh));
        setJMenuBar(new MainMenuBar(
                new AddRepositoryAction(git, repositoriesPane),
                new CloneAction(git, repositoriesPane),
                new CloseRepositoryAction(repositoriesPane),
                removeRepository, commit, pull, push, newBranch, renameBranch, deleteBranch, merge, abortMerge,
                newTag, pushTag, renameTag, deleteTag, stage, unstage, discard, deleteFiles));
        filesPane.installContextActions(deleteFiles, stage, unstage, null, discard, deleteFiles);
        // Other repositories are not watched; re-check them when the user comes back to the window.
        addWindowFocusListener(new WindowAdapter() {
            @Override
            public void windowGainedFocus(WindowEvent e) {
                repositoriesPane.refreshAll();
            }
        });
        repositoriesPane.addOpenListener(opened -> {
            openedRepository = opened;
            updateTitle();
            diffPane.showRepository(opened);
            filesPane.showRepository(opened);
            branchesPane.showRepository(opened);
            historyPane.showRepository(opened);
            autoRefresh.watch(opened);
        });
        mainContent = new MainContent(repositoriesPane, branchesPane, filesPane, diffPane, historyPane);
        setContentPane(createContent(Map.of("commit", commit, "merge", merge, "pull", pull, "push", push,
                        "stage", stage, "unstage", unstage, "discard", discard, "delete", deleteFiles),
                mainContent));

        setMinimumSize(new Dimension(640, 400));
        setSize(fitToScreen(1280, 820));
        setLocationRelativeTo(null);
        normalBounds = getBounds();
        addComponentListener(new ComponentAdapter() {
            @Override
            public void componentMoved(ComponentEvent e) {
                rememberNormalBounds();
            }

            @Override
            public void componentResized(ComponentEvent e) {
                rememberNormalBounds();
            }
        });
    }

    /** Application version shown in the title, e.g. {@code 0.3.0}; {@code null} if unknown. Call on the EDT. */
    public void setVersion(String version) {
        this.version = version;
        updateTitle();
    }

    private void updateTitle() {
        setTitle(WindowTitle.of(openedRepository, version));
    }

    /** Current geometry, to be saved and passed to {@link #applyLayout} on the next start. */
    public WindowLayout currentLayout() {
        return new WindowLayout(normalBounds.x, normalBounds.y, normalBounds.width, normalBounds.height,
                isMaximized(), mainContent.dividers());
    }

    /**
     * Restores a saved geometry. Call on the EDT before the window is shown. A position that is no longer
     * on any screen (e.g. a disconnected monitor) is ignored and the window stays centered.
     */
    public void applyLayout(WindowLayout layout) {
        Rectangle bounds = new Rectangle(layout.x(), layout.y(), layout.width(), layout.height());
        if (isOnScreen(bounds, screenBounds())) {
            Dimension min = getMinimumSize();
            bounds.width = Math.max(bounds.width, min.width);
            bounds.height = Math.max(bounds.height, min.height);
            setBounds(bounds);
            normalBounds = bounds;
        }
        if (layout.maximized()) {
            setExtendedState(getExtendedState() | Frame.MAXIMIZED_BOTH);
        }
        mainContent.restoreDividers(layout.dividers());
    }

    /** True if enough of the window's title strip lies on one of the screens to grab it with the mouse. */
    static boolean isOnScreen(Rectangle bounds, List<Rectangle> screens) {
        if (bounds.width <= 0 || bounds.height <= 0) {
            return false;
        }
        Rectangle titleStrip = new Rectangle(bounds.x, bounds.y, bounds.width, Math.min(bounds.height, 30));
        for (Rectangle screen : screens) {
            Rectangle visible = screen.intersection(titleStrip);
            if (visible.width >= MIN_VISIBLE_WIDTH && visible.height >= MIN_VISIBLE_HEIGHT) {
                return true;
            }
        }
        return false;
    }

    private static List<Rectangle> screenBounds() {
        List<Rectangle> screens = new ArrayList<>();
        for (GraphicsDevice device : GraphicsEnvironment.getLocalGraphicsEnvironment().getScreenDevices()) {
            screens.add(device.getDefaultConfiguration().getBounds());
        }
        return screens;
    }

    private boolean isMaximized() {
        return (getExtendedState() & Frame.MAXIMIZED_BOTH) == Frame.MAXIMIZED_BOTH;
    }

    private void rememberNormalBounds() {
        if ((getExtendedState() & (Frame.MAXIMIZED_BOTH | Frame.ICONIFIED)) == 0) {
            normalBounds = getBounds();
        }
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

    /**
     * Like {@link #reopen(Path)}, then selects {@code file} (repository-relative) in the file list
     * once the status is read, if it still has changes. Call on the EDT.
     */
    public void reopen(Path workTree, Optional<String> file) {
        if (repositoriesPane.open(workTree)) {
            file.ifPresent(filesPane::selectWhenLoaded);
        }
    }

    /** Repository-relative path of the single file selected in the file list, if any. Call on the EDT. */
    public Optional<String> selectedFile() {
        return filesPane.selectedFile().map(FileChange::path);
    }

    static JPanel createContent(Map<String, Action> toolbarActions, MainContent mainContent) {
        JPanel root = new JPanel(new BorderLayout());
        root.add(new MainToolBar(toolbarActions), BorderLayout.NORTH);
        root.add(mainContent, BorderLayout.CENTER);
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
