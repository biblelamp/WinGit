package cz.wingit.ui.branches;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
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
import cz.wingit.core.refs.LocalBranch;
import cz.wingit.ui.Theme;

class BranchesPaneTest {

    private static final PersonIdent IDENT = new PersonIdent("Test", "test@example.invalid");

    @TempDir
    Path repo;

    @BeforeAll
    static void installTheme() {
        Theme.install(Theme.Mode.LIGHT);
    }

    @Test
    void showsRefsAndKeepsExpandedGroupsOnRefresh() throws Exception {
        try (Git git = Git.init().setDirectory(repo.toFile()).setInitialBranch("main").call()) {
            Files.writeString(repo.resolve("a.txt"), "a", StandardCharsets.UTF_8);
            git.add().addFilepattern("a.txt").call();
            git.commit().setMessage("init").setAuthor(IDENT).setCommitter(IDENT).setSign(false).call();
            git.branchCreate().setName("feature").call();
            git.tag().setName("v1").setTagger(IDENT).setMessage("v1").setSigned(false).call();
            Files.writeString(repo.resolve("a.txt"), "wip", StandardCharsets.UTF_8);
            git.stashCreate().setPerson(IDENT).setWorkingDirectoryMessage("wip").call();
        }

        AtomicReference<BranchesPane> pane = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> {
            pane.set(new BranchesPane(new DefaultGitService(new GitCli())));
            pane.get().showRepository(Optional.of(repo));
        });

        assertThat(awaitRows(pane.get(), 6)).containsExactly(
                "Local (2)", "feature", "main", "Remotes (0)", "Tags (1)", "Stashes (1)");

        // expand Tags (row 4), add a tag, refresh: Tags stays expanded and shows both
        SwingUtilities.invokeAndWait(() -> pane.get().expandRowForTest(4));
        try (Git git = Git.open(repo.toFile())) {
            git.tag().setName("v2").setTagger(IDENT).setMessage("v2").setSigned(false).call();
        }
        SwingUtilities.invokeAndWait(() -> pane.get().refresh());

        // Stashes was not expanded, so it stays collapsed
        assertThat(awaitRows(pane.get(), 8)).containsExactly(
                "Local (2)", "feature", "main", "Remotes (0)", "Tags (2)", "v1", "v2", "Stashes (1)");
    }

    @Test
    void trackingDetailShowsUpstreamAndCounts() {
        assertThat(BranchTreeBuilder.trackingDetail(new LocalBranch("main", null, 0, 0, true))).isNull();
        assertThat(BranchTreeBuilder.trackingDetail(new LocalBranch("main", "origin/main", 0, 0, true)))
                .isEqualTo("origin/main");
        assertThat(BranchTreeBuilder.trackingDetail(new LocalBranch("main", "origin/main", 2, 1, true)))
                .isEqualTo("origin/main ↑2 ↓1");
    }

    private static List<String> awaitRows(BranchesPane pane, int expected) throws Exception {
        AtomicReference<List<String>> rows = new AtomicReference<>();
        long deadline = System.currentTimeMillis() + 10_000;
        do {
            SwingUtilities.invokeAndWait(() -> rows.set(pane.visibleRows()));
            if (rows.get().size() == expected) {
                return rows.get();
            }
            Thread.sleep(50);
        } while (System.currentTimeMillis() < deadline);
        return rows.get();
    }
}
