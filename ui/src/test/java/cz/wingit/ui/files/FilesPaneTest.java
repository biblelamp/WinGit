package cz.wingit.ui.files;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import javax.swing.SwingUtilities;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.PersonIdent;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import cz.wingit.core.DefaultGitService;
import cz.wingit.core.cli.GitCli;
import cz.wingit.core.status.ChangeType;
import cz.wingit.core.status.FileChange;
import cz.wingit.ui.Theme;
import cz.wingit.ui.common.Icons;

class FilesPaneTest {

    @TempDir
    Path repo;

    @BeforeAll
    static void installTheme() {
        Theme.install(Theme.Mode.LIGHT);
    }

    @Test
    void showsChangedFilesOfRepositoryAndFilters() throws Exception {
        PersonIdent ident = new PersonIdent("Test", "test@example.invalid");
        try (Git git = Git.init().setDirectory(repo.toFile()).call()) {
            write("readme.txt", "a\n");
            write("src/app/Main.java", "class Main {}\n");
            git.add().addFilepattern(".").call();
            git.commit().setMessage("init").setAuthor(ident).setCommitter(ident).setSign(false).call();
        }
        write("readme.txt", "changed\n");
        write("src/app/Main.java", "class Main { }\n");
        write("notes.md", "new\n");

        FilesPane[] pane = new FilesPane[1];
        SwingUtilities.invokeAndWait(() -> {
            pane[0] = new FilesPane(new DefaultGitService(new GitCli()));
            pane[0].showRepository(Optional.of(repo));
        });
        awaitRows(pane[0], 3);

        // selecting one row reports that file
        List<Optional<FileChange>> selections = new ArrayList<>();
        SwingUtilities.invokeAndWait(() -> {
            pane[0].addFileSelectionListener(selections::add);
            pane[0].selectRowForTest(0);
        });
        assertThat(selections).hasSize(1);
        assertThat(selections.get(0)).isPresent();

        // a double-click opens that file (in a diff window of its own)
        List<FileChange> opened = new ArrayList<>();
        SwingUtilities.invokeAndWait(() -> {
            pane[0].addFileOpenListener(opened::add);
            pane[0].openRowForTest(0);
            pane[0].openRowForTest(-1);
        });
        assertThat(opened).containsExactly(selections.get(0).get());

        // Enter opens the one selected file, but not when several are selected
        opened.clear();
        SwingUtilities.invokeAndWait(() -> {
            pane[0].pressEnterForTest();
            pane[0].selectAllRowsForTest();
            pane[0].pressEnterForTest();
            pane[0].selectRowForTest(0);
        });
        assertThat(opened).containsExactly(selections.get(0).get());

        AtomicInteger filtered = new AtomicInteger();
        SwingUtilities.invokeAndWait(() -> {
            pane[0].setFilterText("SRC\\app");
            filtered.set(pane[0].visibleRowCount());
        });
        assertThat(filtered.get()).isEqualTo(1);

        AtomicInteger cleared = new AtomicInteger(-1);
        SwingUtilities.invokeAndWait(() -> {
            pane[0].showRepository(Optional.empty());
            cleared.set(pane[0].visibleRowCount());
        });
        assertThat(cleared.get()).isZero();
    }

    @Test
    void fileSelectedInTheLastSessionIsSelectedAgainAfterLoading() throws Exception {
        try (Git git = Git.init().setDirectory(repo.toFile()).call()) {
            // empty repository: every file is untracked
        }
        write("a.txt", "a\n");
        write("dir/b.txt", "b\n");

        FilesPane[] pane = new FilesPane[1];
        List<Optional<FileChange>> selections = new ArrayList<>();
        SwingUtilities.invokeAndWait(() -> {
            pane[0] = new FilesPane(new DefaultGitService(new GitCli()));
            pane[0].addFileSelectionListener(selections::add);
            pane[0].showRepository(Optional.of(repo));
            pane[0].selectWhenLoaded("dir/b.txt");
        });
        awaitRows(pane[0], 2);

        AtomicReference<Optional<FileChange>> selected = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> selected.set(pane[0].selectedFile()));
        assertThat(selected.get()).map(FileChange::path).contains("dir/b.txt");
        assertThat(selections).last().isEqualTo(selected.get()); // the diff follows

        // a file without changes any more is simply not selected
        SwingUtilities.invokeAndWait(() -> {
            pane[0].showRepository(Optional.of(repo));
            pane[0].selectWhenLoaded("gone.txt");
        });
        awaitRows(pane[0], 2);
        SwingUtilities.invokeAndWait(() -> selected.set(pane[0].selectedFile()));
        assertThat(selected.get()).isEmpty();
    }

    @Test
    void refreshPicksUpNewChangesWithoutClearing() throws Exception {
        try (Git git = Git.init().setDirectory(repo.toFile()).call()) {
            // empty repository: every file is untracked
        }
        write("first.txt", "1\n");

        FilesPane[] pane = new FilesPane[1];
        SwingUtilities.invokeAndWait(() -> {
            pane[0] = new FilesPane(new DefaultGitService(new GitCli()));
            pane[0].showRepository(Optional.of(repo));
        });
        awaitRows(pane[0], 1);

        write("second.txt", "2\n");
        AtomicInteger rowsRightAfterRefresh = new AtomicInteger();
        SwingUtilities.invokeAndWait(() -> {
            pane[0].refresh();
            rowsRightAfterRefresh.set(pane[0].visibleRowCount());
        });

        assertThat(rowsRightAfterRefresh.get()).as("old rows stay while reading").isEqualTo(1);
        awaitRows(pane[0], 2);
    }

    @Test
    void everyChangeTypeHasIconAndWorkingTreeChangeWins() {
        for (ChangeType type : ChangeType.values()) {
            assertThat(Icons.fileStatus(type).hasFound()).as("icon for %s", type).isTrue();
        }
        assertThat(FileNameCellRenderer.iconType(new FileChange("a", ChangeType.ADDED, ChangeType.MODIFIED)))
                .isEqualTo(ChangeType.MODIFIED);
        assertThat(FileNameCellRenderer.iconType(new FileChange("a", ChangeType.ADDED, null)))
                .isEqualTo(ChangeType.ADDED);
    }

    @Test
    void statusTextCombinesStagedAndUnstagedChange() {
        assertThat(FileChangeTableModel.statusText(new FileChange("a", null, ChangeType.UNTRACKED)))
                .isEqualTo("Untracked");
        assertThat(FileChangeTableModel.statusText(new FileChange("a", ChangeType.ADDED, null)))
                .isEqualTo("Added (staged)");
        assertThat(FileChangeTableModel.statusText(new FileChange("a", ChangeType.MODIFIED, ChangeType.DELETED)))
                .isEqualTo("Modified (staged), Deleted");
        assertThat(FileChangeTableModel.statusText(new FileChange("a", null, ChangeType.CONFLICTED)))
                .isEqualTo("Conflict");
        assertThat(FileChangeTableModel.statusText(FileChange.renamed("old/a", "new/a", ChangeType.MODIFIED)))
                .isEqualTo("Renamed (staged), Modified");
    }

    @Test
    void renamedFileShowsItsOriginalPathInTooltip() {
        assertThat(FileNameCellRenderer.tooltip(FileChange.renamed("old/a.txt", "new/a.txt", null)))
                .isEqualTo("Renamed from " + "old/a.txt".replace('/', java.io.File.separatorChar));
        assertThat(FileNameCellRenderer.tooltip(new FileChange("a.txt", null, ChangeType.MODIFIED))).isNull();
    }

    @Test
    void fileActionsFollowSelectionAndStageWorks() throws Exception {
        PersonIdent ident = new PersonIdent("Test", "test@example.invalid");
        try (Git git = Git.init().setDirectory(repo.toFile()).call()) {
            write("readme.txt", "a\n");
            git.add().addFilepattern(".").call();
            git.commit().setMessage("init").setAuthor(ident).setCommitter(ident).setSign(false).call();
        }
        write("readme.txt", "changed\n");
        DefaultGitService service = new DefaultGitService(new GitCli());

        FilesPane[] pane = new FilesPane[1];
        FileAction[] actions = new FileAction[4];
        SwingUtilities.invokeAndWait(() -> {
            pane[0] = new FilesPane(service);
            actions[0] = FileAction.stage(service, pane[0], pane[0]::refresh);
            actions[1] = FileAction.unstage(service, pane[0], pane[0]::refresh);
            actions[2] = FileAction.discard(service, pane[0], pane[0]::refresh);
            actions[3] = FileAction.delete(service, pane[0], pane[0]::refresh);
            pane[0].showRepository(Optional.of(repo));
        });
        awaitRows(pane[0], 1);

        SwingUtilities.invokeAndWait(() -> {
            assertThat(actions[0].isEnabled()).isFalse(); // nothing selected yet
            pane[0].selectRowForTest(0);
            assertThat(actions[0].isEnabled()).isTrue();  // unstaged change
            assertThat(actions[1].isEnabled()).isFalse(); // nothing staged
            assertThat(actions[2].isEnabled()).isTrue();
            assertThat(actions[3].isEnabled()).isTrue();
            actions[0].actionPerformed(null);
        });

        long deadline = System.currentTimeMillis() + 10_000;
        boolean[] unstageEnabled = new boolean[1];
        while (!unstageEnabled[0] && System.currentTimeMillis() < deadline) {
            Thread.sleep(50);
            SwingUtilities.invokeAndWait(() -> unstageEnabled[0] = actions[1].isEnabled());
        }
        assertThat(service.status(repo).changes())
                .containsExactly(new FileChange("readme.txt", ChangeType.MODIFIED, null));
        assertThat(unstageEnabled[0]).isTrue(); // refreshed row keeps its selection
        SwingUtilities.invokeAndWait(() -> assertThat(actions[0].isEnabled()).isFalse());
    }

    @Test
    void discardRefreshesTheList() throws Exception {
        PersonIdent ident = new PersonIdent("Test", "test@example.invalid");
        try (Git git = Git.init().setDirectory(repo.toFile()).call()) {
            write("readme.txt", "a\n");
            git.add().addFilepattern(".").call();
            git.commit().setMessage("init").setAuthor(ident).setCommitter(ident).setSign(false).call();
        }
        write("readme.txt", "changed\n");
        write("new.txt", "new\n");
        DefaultGitService service = new DefaultGitService(new GitCli());

        FilesPane[] pane = new FilesPane[1];
        SwingUtilities.invokeAndWait(() -> {
            pane[0] = new FilesPane(service);
            // actions listen to the selection: they must not break the refresh when rows disappear
            FileAction.stage(service, pane[0], () -> { });
            FileAction.discard(service, pane[0], () -> { });
            pane[0].showRepository(Optional.of(repo));
        });
        awaitRows(pane[0], 2);

        SwingUtilities.invokeAndWait(() -> {
            pane[0].selectAllRowsForTest();
        });
        service.discard(repo, service.status(repo).changes());
        SwingUtilities.invokeAndWait(pane[0]::refresh);

        awaitRows(pane[0], 0);
        int[] rows = new int[1];
        SwingUtilities.invokeAndWait(() -> rows[0] = pane[0].visibleRowCount());
        assertThat(rows[0]).isZero();
    }

    private static void awaitRows(FilesPane pane, int expected) throws Exception {
        AtomicInteger rows = new AtomicInteger();
        long deadline = System.currentTimeMillis() + 10_000;
        while (System.currentTimeMillis() < deadline) {
            SwingUtilities.invokeAndWait(() -> rows.set(pane.visibleRowCount()));
            if (rows.get() == expected) {
                return;
            }
            Thread.sleep(50);
        }
        assertThat(rows.get()).as("rows after loading").isEqualTo(expected);
    }

    private void write(String relative, String content) throws Exception {
        Path file = repo.resolve(relative);
        Files.createDirectories(file.getParent());
        Files.writeString(file, content, StandardCharsets.UTF_8);
    }
}
