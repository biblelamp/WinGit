package cz.wingit.ui.main;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Rectangle;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Supplier;

import javax.swing.JFrame;
import javax.swing.Action;
import javax.swing.JPanel;
import javax.swing.WindowConstants;

import com.formdev.flatlaf.extras.FlatSVGUtils;

import cz.wingit.core.GitService;
import cz.wingit.core.repo.RepositoryList;
import cz.wingit.core.status.FileChange;
import cz.wingit.ui.Messages;
import cz.wingit.ui.common.FrameGeometry;
import cz.wingit.ui.common.WindowLayout;
import cz.wingit.ui.about.HelpActions;
import cz.wingit.ui.branches.BranchesPane;
import cz.wingit.ui.branches.DeleteBranchAction;
import cz.wingit.ui.branches.MergeAction;
import cz.wingit.ui.branches.NewBranchAction;
import cz.wingit.ui.branches.RenameBranchAction;
import cz.wingit.ui.commit.CommitAction;
import cz.wingit.ui.diff.DiffPane;
import cz.wingit.ui.diff.DiffWindow;
import cz.wingit.ui.files.FileAction;
import cz.wingit.ui.files.FilesPane;
import cz.wingit.ui.history.HistoryPane;
import cz.wingit.ui.log.LogAction;
import cz.wingit.ui.log.LogWindow;
import cz.wingit.ui.remote.AutoFetch;
import cz.wingit.ui.remote.RemoteAction;
import cz.wingit.ui.repositories.AddRepositoryAction;
import cz.wingit.ui.repositories.CloneAction;
import cz.wingit.ui.repositories.CloseRepositoryAction;
import cz.wingit.ui.repositories.RemoveRepositoryAction;
import cz.wingit.ui.reposettings.RepositorySettingsAction;
import cz.wingit.ui.repositories.RepositoriesPane;
import cz.wingit.ui.settings.Appearance;
import cz.wingit.ui.settings.FetchInterval;
import cz.wingit.ui.settings.Preferences;
import cz.wingit.ui.settings.SettingsAction;
import cz.wingit.ui.stash.SelectedStashAction;
import cz.wingit.ui.stash.StashAction;
import cz.wingit.ui.tags.NewTagAction;
import cz.wingit.ui.tags.SelectedTagAction;

/**
 * Top-level application window: menu, toolbar, panes, status bar.
 */
public final class MainWindow extends JFrame {

    private static final long serialVersionUID = 1L;

    private final RepositoriesPane repositoriesPane;
    private final MainContent mainContent;
    private final FilesPane filesPane;
    private final transient GitService git;
    private final transient AutoFetch autoFetch;
    private final CommitAction commitAction;
    private Optional<Path> openedRepository = Optional.empty();
    private String version;
    /** The SSH key of the application settings, once they are installed. */
    private transient Supplier<Optional<Path>> globalSshKey = Optional::empty;
    /** Normal bounds and maximized state, for saving and restoring. */
    private final transient FrameGeometry geometry;

    public MainWindow(GitService git, RepositoryList repositories) {
        super(Messages.get("mainWindow.title"));
        this.git = git;
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
        filesPane.addFileOpenListener(file -> filesPane.repository()
                .ifPresent(repo -> DiffWindow.open(this, git, repo, file)));
        filesPane.addStatusListener((path, status) -> repositoriesPane.updateChanged(path, !status.isClean()));
        filesPane.addStatusListener((path, status) -> DiffWindow.statusChanged(path, status.changes()));
        Runnable refresh = () -> {
            filesPane.refresh();
            branchesPane.refresh();
            historyPane.refresh();
            diffPane.refresh();
            DiffWindow.refreshAll();
            LogWindow.refreshAll();
            repositoriesPane.openedRepository().ifPresent(repositoriesPane::refreshBranch);
        };
        AutoRefresh autoRefresh = new AutoRefresh(git, refresh);
        autoFetch = new AutoFetch(git, repositoriesPane::paths, fetched -> {
            if (repositoriesPane.openedRepository().filter(fetched::equals).isPresent()) {
                refresh.run(); // the history shows the new commits, the list their count
            } else {
                repositoriesPane.refreshBranch(fetched);
            }
        });
        Action fetch = RemoteAction.fetch(git, repositoriesPane, refresh);
        commitAction = new CommitAction(git, repositoriesPane, filesPane::selectedChanges, refresh);
        Action commit = commitAction;
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
        Action stash = new StashAction(git, repositoriesPane, refresh);
        Action applyStash = SelectedStashAction.apply(git, branchesPane, refresh);
        Action popStash = SelectedStashAction.pop(git, branchesPane, refresh);
        Action deleteStash = SelectedStashAction.delete(git, branchesPane, refresh);
        branchesPane.installStashActions(deleteStash, stash, applyStash, popStash);
        historyPane.installContextActions(
                NewTagAction.onCommit(git, repositoriesPane, historyPane::selectedCommit, refresh));
        historyPane.addOpenListener(opened -> repositoriesPane.openedRepository()
                .ifPresent(repo -> LogWindow.open(this, git, repo, Optional.of(opened.id()))));
        Action history = new LogAction(git, repositoriesPane, this);
        MainMenuBar menuBar = new MainMenuBar(
                new AddRepositoryAction(git, repositoriesPane),
                new CloneAction(git, repositoriesPane),
                new CloseRepositoryAction(repositoriesPane),
                removeRepository, commit, fetch, pull, push, newBranch, renameBranch, deleteBranch, merge, abortMerge,
                newTag, pushTag, renameTag, deleteTag, List.of(stash, applyStash, popStash, deleteStash),
                stage, unstage, discard, deleteFiles);
        menuBar.addHelpMenu(HelpActions.website(this), HelpActions.reportIssue(this),
                HelpActions.about(this, git, () -> version));
        RepositorySettingsAction repositorySettings = new RepositorySettingsAction(repositoriesPane, git,
                () -> globalSshKey.get());
        menuBar.addRepositorySettingsAction(repositorySettings);
        repositoriesPane.addContextAction(repositorySettings);
        commitAction.setOpenRepositorySettings(repositorySettings::open); // the Git user icon of the commit dialog
        setJMenuBar(menuBar);
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
            opened.ifPresent(autoFetch::fetch);
        });
        autoFetch.start();
        mainContent = new MainContent(repositoriesPane, branchesPane, filesPane, diffPane, historyPane);
        setContentPane(createContent(Map.ofEntries(Map.entry("commit", commit), Map.entry("merge", merge),
                        Map.entry("fetch", fetch), Map.entry("pull", pull), Map.entry("push", push),
                        Map.entry("stage", stage), Map.entry("unstage", unstage), Map.entry("discard", discard),
                        Map.entry("delete", deleteFiles), Map.entry("stash", stash), Map.entry("history", history)),
                mainContent));

        setMinimumSize(new Dimension(640, 400));
        setSize(FrameGeometry.fitToScreen(1280, 820));
        setLocationRelativeTo(null);
        geometry = new FrameGeometry(this);
    }

    /** Application version shown in the title, e.g. {@code 0.3.0}; {@code null} if unknown. Call on the EDT. */
    /**
     * Adds Repository → Settings…; {@code active} is what this run uses, {@code load}/{@code save} access the
     * stored settings ({@code save} may throw {@link java.io.UncheckedIOException}); {@code restart}, if this
     * run can restart itself, saves the state, starts a new instance and exits.
     */
    public void installSettings(Appearance active, Supplier<Preferences> load, Consumer<Preferences> save,
                                Optional<Runnable> restart) {
        Consumer<Preferences> saveAndApply = chosen -> {
            save.accept(chosen);
            setFetchInterval(chosen.fetch());
            git.setSshKey(chosen.sshKey());
        };
        globalSshKey = () -> load.get().sshKey();
        SettingsAction settings = new SettingsAction(this, active, load, git, saveAndApply, restart);
        ((MainMenuBar) getJMenuBar()).addSettingsAction(settings);
    }

    /** How often all repositories are fetched in the background (or never). Call on the EDT. */
    public void setFetchInterval(FetchInterval interval) {
        autoFetch.setInterval(interval.duration());
    }

    public void setVersion(String version) {
        this.version = version;
        updateTitle();
    }

    private void updateTitle() {
        setTitle(WindowTitle.of(openedRepository, version));
    }

    /** Current geometry, to be saved and passed to {@link #applyLayout} on the next start. */
    public WindowLayout currentLayout() {
        return geometry.layout(mainContent.dividers());
    }

    /**
     * Restores a saved geometry. Call on the EDT before the window is shown. A position that is no longer
     * on any screen (e.g. a disconnected monitor) is ignored and the window stays centered.
     */
    public void applyLayout(WindowLayout layout) {
        geometry.apply(layout);
        mainContent.restoreDividers(layout.dividers());
    }

    /** True if enough of the window's title strip lies on one of the screens to grab it with the mouse. */
    static boolean isOnScreen(Rectangle bounds, List<Rectangle> screens) {
        return FrameGeometry.isOnScreen(bounds, screens);
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
}
