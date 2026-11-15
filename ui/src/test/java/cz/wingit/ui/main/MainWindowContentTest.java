package cz.wingit.ui.main;

import static org.assertj.core.api.Assertions.assertThat;

import java.awt.Component;
import java.awt.Container;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import javax.swing.AbstractButton;
import javax.swing.JList;
import javax.swing.JMenu;
import javax.swing.JPanel;
import javax.swing.KeyStroke;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import cz.wingit.core.DefaultGitService;
import cz.wingit.core.GitService;
import cz.wingit.core.cli.GitCli;
import cz.wingit.core.repo.RepositoryList;
import cz.wingit.ui.Theme;
import cz.wingit.ui.branches.BranchesPane;
import cz.wingit.ui.commit.CommitAction;
import cz.wingit.ui.diff.DiffPane;
import cz.wingit.ui.files.FilesPane;
import cz.wingit.ui.history.HistoryPane;
import cz.wingit.ui.remote.RemoteAction;
import cz.wingit.ui.repositories.AddRepositoryAction;
import cz.wingit.ui.repositories.CloneAction;
import cz.wingit.ui.repositories.CloseRepositoryAction;
import cz.wingit.ui.repositories.RemoveRepositoryAction;
import cz.wingit.ui.repositories.RepositoriesPane;

/**
 * Builds the main-window components headlessly: catches missing resource keys
 * and construction errors without opening a window.
 */
class MainWindowContentTest {

    @TempDir
    Path tempDir;

    private final GitService git = new DefaultGitService(new GitCli());
    private RepositoriesPane repositoriesPane;

    @BeforeAll
    static void installTheme() {
        Theme.install(Theme.Mode.LIGHT);
    }

    @BeforeEach
    void createPane() {
        repositoriesPane = new RepositoriesPane(git, RepositoryList.load(tempDir.resolve("repos.txt")));
    }

    @Test
    void contentBuildsWithAllPanes() {
        JPanel content = MainWindow.createContent(Map.of(), repositoriesPane, new BranchesPane(git),
                new FilesPane(git), new DiffPane(git), new HistoryPane(git));
        assertThat(content.getComponentCount()).isEqualTo(3);
    }

    @Test
    void toolbarButtonsFollowTheirActions() {
        RemoteAction push = RemoteAction.push(git, repositoriesPane, () -> { });
        MainToolBar toolBar = new MainToolBar(Map.of("push", push));

        AbstractButton pushButton = findButton(toolBar, "Push");
        AbstractButton mergeButton = findButton(toolBar, "Merge");
        assertThat(pushButton.getIcon()).isNotNull();
        assertThat(pushButton.getToolTipText()).isEqualTo("Push (Ctrl+Shift+K)");
        assertThat(pushButton.isEnabled()).isFalse(); // no repository open yet

        push.setEnabled(true);
        assertThat(pushButton.isEnabled()).isTrue();
        assertThat(pushButton.getText()).isEqualTo("Push");
        assertThat(mergeButton.isEnabled()).isFalse(); // not implemented yet
    }

    @Test
    void menuHasRepositoryLocalAndRemoteMenus() {
        FilesPane filesPane = new FilesPane(git);
        MainMenuBar menuBar = new MainMenuBar(
                new AddRepositoryAction(git, repositoriesPane),
                new CloneAction(git, repositoriesPane),
                new CloseRepositoryAction(repositoriesPane),
                new RemoveRepositoryAction(repositoriesPane),
                new CommitAction(git, repositoriesPane, filesPane::selectedChanges, () -> { }),
                RemoteAction.pull(git, repositoriesPane, () -> { }),
                RemoteAction.push(git, repositoriesPane, () -> { }));

        assertThat(menuBar.getMenuCount()).isEqualTo(3);
        JMenu local = menuBar.getMenu(1);
        assertThat(local.getText()).isEqualTo("Local");
        assertThat(local.getItem(0).getText()).isEqualTo("Commit…");
        assertThat(local.getItem(0).getAccelerator()).isEqualTo(KeyStroke.getKeyStroke("ctrl K"));
        assertThat(local.getItem(0).isEnabled()).isFalse();
        JMenu remote = menuBar.getMenu(2);
        assertThat(remote.getText()).isEqualTo("Remote");
        assertThat(remote.getItem(0).getText()).isEqualTo("Pull");
        assertThat(remote.getItem(0).getAccelerator()).isEqualTo(KeyStroke.getKeyStroke("ctrl T"));
        assertThat(remote.getItem(1).getText()).isEqualTo("Push");
        assertThat(remote.getItem(1).getAccelerator()).isEqualTo(KeyStroke.getKeyStroke("ctrl shift K"));
        assertThat(remote.getItem(1).isEnabled()).isFalse();

        JMenu repository = menuBar.getMenu(0);
        assertThat(repository.getText()).isEqualTo("Repository");
        assertThat(repository.getItem(0).getText()).isEqualTo("Add or Create…");
        assertThat(repository.getItem(1).getText()).isEqualTo("Clone…");
        assertThat(repository.getItem(2)).isNull(); // separator
        assertThat(repository.getItem(3).getText()).isEqualTo("Close");
        assertThat(repository.getItem(4).getText()).isEqualTo("Remove from List");
        assertThat(repository.getItem(5)).isNull(); // separator
        assertThat(repository.getItem(6).getText()).isEqualTo("Exit");

        assertThat(repository.getItem(0).getAccelerator()).isEqualTo(KeyStroke.getKeyStroke("ctrl O"));
        assertThat(repository.getItem(1).getAccelerator()).isEqualTo(KeyStroke.getKeyStroke("ctrl shift O"));
        assertThat(repository.getItem(3).getAccelerator()).isEqualTo(KeyStroke.getKeyStroke("ctrl W"));
        assertThat(repository.getItem(4).getAccelerator()).isNull();
        assertThat(repository.getItem(6).getAccelerator()).isEqualTo(KeyStroke.getKeyStroke("alt X"));
        assertThat(repository.getItem(0).getMnemonic()).isEqualTo('A');
        assertThat(repository.getItem(1).getMnemonic()).isEqualTo('C');
        assertThat(repository.getItem(3).getMnemonic()).isEqualTo('O');
        assertThat(repository.getItem(4).getMnemonic()).isEqualTo('R');
        assertThat(repository.getItem(6).getMnemonic()).isEqualTo('X');
        // Nothing is open yet, so there is nothing to close.
        assertThat(repository.getItem(3).isEnabled()).isFalse();
    }

    @Test
    void removeIsEnabledOnlyWithSelectionAndForgetsRepository() throws Exception {
        Path store = tempDir.resolve("list.txt");
        Path first = Files.createDirectory(tempDir.resolve("first"));
        Path second = Files.createDirectory(tempDir.resolve("second"));
        RepositoryList list = RepositoryList.load(store);
        list.add(first);
        list.add(second);
        RepositoriesPane pane = new RepositoriesPane(git, list);
        RemoveRepositoryAction remove = new RemoveRepositoryAction(pane);

        assertThat(remove.isEnabled()).isFalse();

        pane.addRepository(first); // already present: just selects it
        assertThat(pane.selectedRepository()).contains(first);
        assertThat(remove.isEnabled()).isTrue();

        pane.removeRepository(first);

        assertThat(RepositoryList.load(store).paths()).containsExactly(second);
        assertThat(pane.selectedRepository()).contains(second);
        assertThat(Files.isDirectory(first)).isTrue();
    }

    @Test
    void selectingDoesNotOpenButOpenSelectedDoes() throws Exception {
        Path first = Files.createDirectory(tempDir.resolve("one"));
        Path second = Files.createDirectory(tempDir.resolve("two"));
        RepositoryList list = RepositoryList.load(tempDir.resolve("open.txt"));
        list.add(first);
        list.add(second);
        RepositoriesPane pane = new RepositoriesPane(git, list);
        List<Optional<Path>> opened = new ArrayList<>();
        pane.addOpenListener(opened::add);

        findList(pane).setSelectedIndex(0); // a single click selects only
        assertThat(pane.selectedRepository()).contains(first);
        assertThat(opened).isEmpty();
        assertThat(pane.openedRepository()).isEmpty();

        pane.openSelected();
        assertThat(opened).containsExactly(Optional.of(first));
        assertThat(pane.openedRepository()).contains(first);

        pane.removeRepository(first); // removing the opened repository closes it
        assertThat(opened).containsExactly(Optional.of(first), Optional.empty());
        assertThat(pane.openedRepository()).isEmpty();
    }

    private static AbstractButton findButton(Container root, String text) {
        for (Component child : root.getComponents()) {
            if (child instanceof AbstractButton button && text.equals(button.getText())) {
                return button;
            }
        }
        throw new AssertionError("no button " + text);
    }

    private static JList<?> findList(Container root) {
        for (Component child : root.getComponents()) {
            if (child instanceof JList<?> found) {
                return found;
            }
            if (child instanceof Container container) {
                JList<?> nested = findList(container);
                if (nested != null) {
                    return nested;
                }
            }
        }
        return null;
    }
}
