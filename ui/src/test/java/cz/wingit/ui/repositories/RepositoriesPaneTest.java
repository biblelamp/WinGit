package cz.wingit.ui.repositories;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import javax.swing.SwingUtilities;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.PersonIdent;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import cz.wingit.core.DefaultGitService;
import cz.wingit.core.cli.GitCli;
import cz.wingit.core.repo.RepositoryList;
import cz.wingit.ui.Theme;
import cz.wingit.ui.common.Icons;

class RepositoriesPaneTest {

    @TempDir
    Path tempDir;

    @BeforeAll
    static void installTheme() {
        Theme.install(Theme.Mode.LIGHT);
    }

    @Test
    void marksRepositoriesWithChanges() throws Exception {
        Path clean = createRepo("clean");
        Path dirty = createRepo("dirty");
        Files.writeString(dirty.resolve("new.txt"), "x", StandardCharsets.UTF_8);

        RepositoryList list = RepositoryList.load(tempDir.resolve("repos.txt"));
        list.add(clean);
        list.add(dirty);

        AtomicReference<RepositoriesPane> pane = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> pane.set(new RepositoriesPane(new DefaultGitService(new GitCli()), list)));

        List<RepositoryEntry> entries = awaitLoaded(pane.get());
        assertThat(entries).extracting(RepositoryEntry::changed).containsExactly(false, true);
        assertThat(entries).extracting(RepositoryEntry::branch).doesNotContainNull();

        // A status read elsewhere (Files pane) updates the marker immediately.
        SwingUtilities.invokeAndWait(() -> pane.get().updateChanged(dirty, false));
        AtomicReference<List<RepositoryEntry>> after = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> after.set(pane.get().entries()));
        assertThat(after.get()).extracting(RepositoryEntry::changed).containsExactly(false, false);
    }

    @Test
    void openRestoresRepositoryOnlyIfStillListed() throws Exception {
        Path first = createRepo("first");
        Path second = createRepo("second");
        RepositoryList list = RepositoryList.load(tempDir.resolve("open.txt"));
        list.add(first);
        list.add(second);

        AtomicReference<RepositoriesPane> pane = new AtomicReference<>();
        List<Optional<Path>> opened = new ArrayList<>();
        SwingUtilities.invokeAndWait(() -> {
            pane.set(new RepositoriesPane(new DefaultGitService(new GitCli()), list));
            pane.get().addOpenListener(opened::add);
        });

        AtomicReference<Boolean> missing = new AtomicReference<>();
        AtomicReference<Boolean> found = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> {
            missing.set(pane.get().open(tempDir.resolve("gone")));
            found.set(pane.get().open(second));
        });

        assertThat(missing.get()).isFalse();
        assertThat(found.get()).isTrue();
        assertThat(opened).containsExactly(Optional.of(second));
        assertThat(pane.get().selectedRepository()).contains(second);
        assertThat(pane.get().openedRepository()).contains(second);
    }

    @Test
    void closeClearsOpenedRepositoryButKeepsItListed() throws Exception {
        Path repo = createRepo("closing");
        RepositoryList list = RepositoryList.load(tempDir.resolve("close.txt"));
        list.add(repo);

        AtomicReference<RepositoriesPane> pane = new AtomicReference<>();
        AtomicReference<CloseRepositoryAction> close = new AtomicReference<>();
        List<Optional<Path>> opened = new ArrayList<>();
        SwingUtilities.invokeAndWait(() -> {
            pane.set(new RepositoriesPane(new DefaultGitService(new GitCli()), list));
            close.set(new CloseRepositoryAction(pane.get()));
            pane.get().addOpenListener(opened::add);
        });
        assertThat(close.get().isEnabled()).isFalse();

        SwingUtilities.invokeAndWait(() -> pane.get().open(repo));
        assertThat(close.get().isEnabled()).isTrue();

        SwingUtilities.invokeAndWait(() -> close.get().actionPerformed(null));
        assertThat(opened).containsExactly(Optional.of(repo), Optional.empty());
        assertThat(pane.get().openedRepository()).isEmpty();
        assertThat(close.get().isEnabled()).isFalse();
        assertThat(list.paths()).containsExactly(repo);
    }

    @Test
    void addedRepositoryIsSelectedAndOpened() throws Exception {
        Path existing = createRepo("existing");
        Path added = createRepo("added");
        RepositoryList list = RepositoryList.load(tempDir.resolve("add.txt"));
        list.add(existing);

        AtomicReference<RepositoriesPane> pane = new AtomicReference<>();
        List<Optional<Path>> opened = new ArrayList<>();
        SwingUtilities.invokeAndWait(() -> {
            pane.set(new RepositoriesPane(new DefaultGitService(new GitCli()), list));
            pane.get().open(existing);
            pane.get().addOpenListener(opened::add);
            pane.get().addRepository(added);
        });

        assertThat(opened).containsExactly(Optional.of(added));
        assertThat(pane.get().selectedRepository()).contains(added);
        assertThat(pane.get().openedRepository()).contains(added);
    }

    @Test
    void missingFolderIsMarkedAndOfferedForRemoval() throws Exception {
        Path present = createRepo("present");
        Path gone = tempDir.resolve("gone");
        RepositoryList list = RepositoryList.load(tempDir.resolve("missing.txt"));
        list.add(present);
        list.add(gone);

        AtomicReference<RepositoriesPane> pane = new AtomicReference<>();
        List<Path> asked = new ArrayList<>();
        List<Optional<Path>> opened = new ArrayList<>();
        SwingUtilities.invokeAndWait(() -> {
            pane.set(new RepositoriesPane(new DefaultGitService(new GitCli()), list));
            pane.get().addOpenListener(opened::add);
        });

        List<RepositoryEntry> entries = awaitLoaded(pane.get());
        assertThat(entries).extracting(RepositoryEntry::missing).containsExactly(true, false); // sorted: gone, present
        assertThat(entries.get(0).branch()).isNull();

        // Restoring the last session does not open it and does not ask.
        AtomicReference<Boolean> restored = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> {
            pane.get().setConfirmRemoveMissing(path -> {
                asked.add(path);
                return false;
            });
            restored.set(pane.get().open(gone));
        });
        assertThat(restored.get()).isFalse();
        assertThat(asked).isEmpty();

        // Opening by double-click / Enter asks; "No" keeps it in the list, still not opened.
        SwingUtilities.invokeAndWait(() -> pane.get().openSelected());
        assertThat(asked).containsExactly(gone);
        assertThat(list.paths()).contains(gone);
        assertThat(opened).isEmpty();

        // "Yes" removes it from the list.
        SwingUtilities.invokeAndWait(() -> {
            pane.get().setConfirmRemoveMissing(path -> true);
            pane.get().openSelected();
        });
        assertThat(list.paths()).containsExactly(present);
        assertThat(opened).isEmpty();
    }

    @Test
    void repositoryIconsAreBundled() {
        assertThat(Icons.repository(false).hasFound()).isTrue();
        assertThat(Icons.repository(true).hasFound()).isTrue();
        assertThat(Icons.repositoryMissing().hasFound()).isTrue();
    }

    private static List<RepositoryEntry> awaitLoaded(RepositoriesPane pane) throws Exception {
        AtomicReference<List<RepositoryEntry>> entries = new AtomicReference<>();
        long deadline = System.currentTimeMillis() + 10_000;
        do {
            SwingUtilities.invokeAndWait(() -> entries.set(pane.entries()));
            if (entries.get().stream().allMatch(e -> e.changed() != null || e.missing())) {
                return entries.get();
            }
            Thread.sleep(50);
        } while (System.currentTimeMillis() < deadline);
        return entries.get();
    }

    private Path createRepo(String name) throws Exception {
        Path dir = Files.createDirectory(tempDir.resolve(name));
        PersonIdent ident = new PersonIdent("Test", "test@example.invalid");
        try (Git git = Git.init().setDirectory(dir.toFile()).call()) {
            Files.writeString(dir.resolve("readme.txt"), "hello", StandardCharsets.UTF_8);
            git.add().addFilepattern("readme.txt").call();
            git.commit().setMessage("init").setAuthor(ident).setCommitter(ident).setSign(false).call();
        }
        return dir;
    }
}
