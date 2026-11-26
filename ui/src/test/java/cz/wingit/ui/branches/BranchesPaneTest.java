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
import cz.wingit.ui.Messages;
import cz.wingit.ui.Theme;
import cz.wingit.ui.stash.SelectedStashAction;
import cz.wingit.ui.tags.SelectedTagAction;

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

        // no remote, so no remote group
        assertThat(awaitRows(pane.get(), 5)).containsExactly(
                "Local Branches (2)", "feature", "main", "Tags (1)", "Stashes (1)");

        // expand Tags (row 3), add a tag, refresh: Tags stays expanded and shows both
        SwingUtilities.invokeAndWait(() -> pane.get().expandRowForTest(3));
        try (Git git = Git.open(repo.toFile())) {
            git.tag().setName("v2").setTagger(IDENT).setMessage("v2").setSigned(false).call();
        }
        SwingUtilities.invokeAndWait(() -> pane.get().refresh());

        // Stashes was not expanded, so it stays collapsed
        assertThat(awaitRows(pane.get(), 7)).containsExactly(
                "Local Branches (2)", "feature", "main", "Tags (2)", "v1 - init", "v2 - init", "Stashes (1)");
    }

    @Test
    void trackingDetailShowsUpstreamAndCounts() {
        assertThat(BranchTreeBuilder.trackingDetail(new LocalBranch("main", null, 0, 0, true))).isNull();
        assertThat(BranchTreeBuilder.trackingDetail(new LocalBranch("main", "origin/main", 0, 0, true)))
                .isEqualTo("→ origin");
        assertThat(BranchTreeBuilder.trackingDetail(new LocalBranch("main", "origin/main", 2, 1, true)))
                .isEqualTo("→ origin ↑2 ↓1");
        // a different name on the server stays in full
        assertThat(BranchTreeBuilder.trackingDetail(new LocalBranch("main", "origin/trunk", 0, 0, true)))
                .isEqualTo("→ origin/trunk");
        assertThat(BranchTreeBuilder.trackingDetail(new LocalBranch("fix/main", "origin/main", 0, 0, true)))
                .isEqualTo("→ origin/main");
        assertThat(BranchTreeBuilder.trackingDetail(new LocalBranch("main", "origin/fix/main", 0, 0, true)))
                .isEqualTo("→ origin/fix/main");
    }

    @Test
    void trackingTooltipShowsFullUpstream() {
        assertThat(BranchTreeBuilder.trackingTooltip(new LocalBranch("main", null, 0, 0, true))).isNull();
        assertThat(BranchTreeBuilder.trackingTooltip(new LocalBranch("main", "origin/main", 2, 1, true)))
                .isEqualTo(Messages.format("branches.tracking.tooltip", "origin/main")
                        + Messages.format("branches.tracking.ahead", "2")
                        + Messages.format("branches.tracking.behind", "1"));
    }

    @Test
    void doubleClickOnBranchChecksItOut() throws Exception {
        try (Git git = Git.init().setDirectory(repo.toFile()).setInitialBranch("main").call()) {
            Files.writeString(repo.resolve("a.txt"), "a", StandardCharsets.UTF_8);
            git.add().addFilepattern("a.txt").call();
            git.commit().setMessage("init").setAuthor(IDENT).setCommitter(IDENT).setSign(false).call();
            git.branchCreate().setName("feature").call();
        }
        DefaultGitService service = new DefaultGitService(new GitCli());

        AtomicReference<BranchesPane> pane = new AtomicReference<>();
        java.util.concurrent.atomic.AtomicInteger switched = new java.util.concurrent.atomic.AtomicInteger();
        SwingUtilities.invokeAndWait(() -> {
            pane.set(new BranchesPane(service));
            pane.get().addSwitchListener(switched::incrementAndGet);
            pane.get().showRepository(Optional.of(repo));
        });
        assertThat(awaitRows(pane.get(), 6)).contains("feature");

        SwingUtilities.invokeAndWait(() -> {
            pane.get().selectRowForTest("feature");
            assertThat(switched.get()).isZero(); // selecting alone does not switch
            pane.get().switchToSelected();
        });

        long deadline = System.currentTimeMillis() + 10_000;
        while (!service.currentBranch(repo).equals(Optional.of("feature")) && System.currentTimeMillis() < deadline) {
            Thread.sleep(50);
        }
        assertThat(service.currentBranch(repo)).contains("feature");
        deadline = System.currentTimeMillis() + 10_000;
        while (switched.get() == 0 && System.currentTimeMillis() < deadline) {
            Thread.sleep(20);
        }
        assertThat(switched.get()).isEqualTo(1); // e.g. the log window re-reads the history of the new HEAD
    }

    @Test
    void deleteIsOfferedOnlyForLocalBranchesThatAreNotCheckedOut() throws Exception {
        try (Git git = Git.init().setDirectory(repo.toFile()).setInitialBranch("main").call()) {
            Files.writeString(repo.resolve("a.txt"), "a", StandardCharsets.UTF_8);
            git.add().addFilepattern("a.txt").call();
            git.commit().setMessage("init").setAuthor(IDENT).setCommitter(IDENT).setSign(false).call();
            git.branchCreate().setName("feature").call();
        }
        DefaultGitService service = new DefaultGitService(new GitCli());

        AtomicReference<BranchesPane> pane = new AtomicReference<>();
        AtomicReference<DeleteBranchAction> delete = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> {
            pane.set(new BranchesPane(service));
            delete.set(new DeleteBranchAction(service, pane.get(), () -> { }));
            pane.get().showRepository(Optional.of(repo));
        });
        assertThat(awaitRows(pane.get(), 6)).contains("feature");

        SwingUtilities.invokeAndWait(() -> {
            assertThat(delete.get().isEnabled()).isFalse();
            pane.get().selectRowForTest("feature");
            assertThat(pane.get().selectedLocalBranch()).contains("feature");
            assertThat(delete.get().isEnabled()).isTrue();
            pane.get().selectRowForTest("main"); // checked out
            assertThat(pane.get().selectedLocalBranch()).isEmpty();
            assertThat(delete.get().isEnabled()).isFalse();
            assertThat(pane.get().selectedAnyLocalBranch()).contains("main"); // can still be renamed
            assertThat(pane.get().localBranchNames()).containsExactlyInAnyOrder("feature", "main");
        });
    }

    @Test
    void remoteBranchesCanBeDeletedAndUpstreamIsKnown() throws Exception {
        Path source = repo.resolve("source");
        try (Git git = Git.init().setDirectory(source.toFile()).setInitialBranch("main").call()) {
            Files.writeString(source.resolve("a.txt"), "a", StandardCharsets.UTF_8);
            git.add().addFilepattern("a.txt").call();
            git.commit().setMessage("init").setAuthor(IDENT).setCommitter(IDENT).setSign(false).call();
            git.branchCreate().setName("feature").call();
        }
        Path clone = repo.resolve("clone");
        DefaultGitService service = new DefaultGitService(new GitCli());
        service.clone(source.toString(), clone, line -> { });

        AtomicReference<BranchesPane> pane = new AtomicReference<>();
        AtomicReference<DeleteBranchAction> delete = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> {
            pane.set(new BranchesPane(service));
            delete.set(new DeleteBranchAction(service, pane.get(), () -> { }));
            pane.get().showRepository(Optional.of(clone));
        });
        // Local, main, origin (with its URL) right at the top level, Tags, Stashes
        assertThat(awaitRows(pane.get(), 5)).element(2).asString().startsWith("origin (2) ");

        SwingUtilities.invokeAndWait(() -> {
            assertThat(pane.get().upstreamOf("main")).contains(new BranchItem.Target("origin", "main"));
            assertThat(pane.get().upstreamOf("unknown")).isEmpty();
            pane.get().expandRowForTest(2); // origin
            pane.get().selectRowForTest("feature");
            assertThat(pane.get().selectedRemoteBranch()).contains(new BranchItem.Target("origin", "feature"));
            assertThat(pane.get().selectedLocalBranch()).isEmpty();
            assertThat(delete.get().isEnabled()).isTrue();
            assertThat(new RenameBranchAction(service, pane.get(), () -> { }).isEnabled()).isTrue();
            assertThat(pane.get().remoteBranchNames("origin")).containsExactlyInAnyOrder("feature", "main");
        });
    }

    @Test
    void tagActionsFollowTheSelectedTag() throws Exception {
        try (Git git = Git.init().setDirectory(repo.toFile()).setInitialBranch("main").call()) {
            Files.writeString(repo.resolve("a.txt"), "a", StandardCharsets.UTF_8);
            git.add().addFilepattern("a.txt").call();
            git.commit().setMessage("init").setAuthor(IDENT).setCommitter(IDENT).setSign(false).call();
            git.tag().setName("v1.0").setTagger(IDENT).setMessage("v1.0").setSigned(false).call();
        }
        DefaultGitService service = new DefaultGitService(new GitCli());

        AtomicReference<BranchesPane> pane = new AtomicReference<>();
        AtomicReference<SelectedTagAction> push = new AtomicReference<>();
        AtomicReference<SelectedTagAction> rename = new AtomicReference<>();
        AtomicReference<SelectedTagAction> delete = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> {
            pane.set(new BranchesPane(service));
            push.set(SelectedTagAction.push(service, pane.get(), () -> { }));
            rename.set(SelectedTagAction.rename(service, pane.get(), () -> { }));
            delete.set(SelectedTagAction.delete(service, pane.get(), () -> { }));
            pane.get().installTagActions(rename.get(), delete.get(), push.get());
            pane.get().showRepository(Optional.of(repo));
        });
        // Local, main, Tags (1), Stashes
        assertThat(awaitRows(pane.get(), 4)).contains("Tags (1)");

        SwingUtilities.invokeAndWait(() -> {
            assertThat(pane.get().selectedTag()).isEmpty();
            assertThat(push.get().isEnabled()).isFalse();
            assertThat(delete.get().isEnabled()).isFalse();
            pane.get().expandRowForTest(2); // Tags
            pane.get().selectRowForTest("v1.0");
            assertThat(pane.get().selectedTag()).contains("v1.0");
            assertThat(pane.get().selectedLocalBranch()).isEmpty();
            assertThat(push.get().isEnabled()).isTrue();
            assertThat(rename.get().isEnabled()).isTrue();
            assertThat(delete.get().isEnabled()).isTrue();
            pane.get().selectRowForTest("main");
            assertThat(pane.get().selectedTag()).isEmpty();
            assertThat(delete.get().isEnabled()).isFalse();
        });
    }

    @Test
    void stashActionsFollowTheSelectedStash() throws Exception {
        try (Git git = Git.init().setDirectory(repo.toFile()).setInitialBranch("main").call()) {
            Files.writeString(repo.resolve("a.txt"), "a", StandardCharsets.UTF_8);
            git.add().addFilepattern("a.txt").call();
            git.commit().setMessage("init").setAuthor(IDENT).setCommitter(IDENT).setSign(false).call();
            Files.writeString(repo.resolve("a.txt"), "wip", StandardCharsets.UTF_8);
            git.stashCreate().setPerson(IDENT).setWorkingDirectoryMessage("wip").call();
        }
        DefaultGitService service = new DefaultGitService(new GitCli());

        AtomicReference<BranchesPane> pane = new AtomicReference<>();
        AtomicReference<SelectedStashAction> apply = new AtomicReference<>();
        AtomicReference<SelectedStashAction> delete = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> {
            pane.set(new BranchesPane(service));
            apply.set(SelectedStashAction.apply(service, pane.get(), () -> { }));
            delete.set(SelectedStashAction.delete(service, pane.get(), () -> { }));
            pane.get().installStashActions(delete.get(), apply.get());
            pane.get().showRepository(Optional.of(repo));
        });
        // Local, main, Tags (0), Stashes (1)
        assertThat(awaitRows(pane.get(), 4)).contains("Stashes (1)");

        SwingUtilities.invokeAndWait(() -> {
            assertThat(pane.get().selectedStash()).isEmpty();
            assertThat(apply.get().isEnabled()).isFalse();
            pane.get().expandRowForTest(3); // Stashes
            pane.get().selectRowForTest("stash@{0}");
            assertThat(pane.get().selectedStash()).hasValueSatisfying(s -> {
                assertThat(s.index()).isZero();
                assertThat(s.message()).isEqualTo("wip");
            });
            assertThat(pane.get().selectedTag()).isEmpty();
            assertThat(apply.get().isEnabled()).isTrue();
            assertThat(delete.get().isEnabled()).isTrue();
            pane.get().selectRowForTest("main");
            assertThat(pane.get().selectedStash()).isEmpty();
            assertThat(delete.get().isEnabled()).isFalse();
        });
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
