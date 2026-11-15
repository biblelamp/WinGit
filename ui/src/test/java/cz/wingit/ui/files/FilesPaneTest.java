package cz.wingit.ui.files;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

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
