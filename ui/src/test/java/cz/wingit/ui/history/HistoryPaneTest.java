package cz.wingit.ui.history;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;

import javax.swing.SwingUtilities;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.PersonIdent;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import cz.wingit.core.DefaultGitService;
import cz.wingit.core.GitService;
import cz.wingit.core.log.CommitInfo;
import cz.wingit.core.cli.GitCli;
import cz.wingit.ui.Theme;

class HistoryPaneTest {

    private static final PersonIdent IDENT = new PersonIdent("Test", "test@example.invalid");

    @TempDir
    Path repo;

    @BeforeAll
    static void installTheme() {
        Theme.install(Theme.Mode.LIGHT);
    }

    @Test
    void showsOnlyNewestCommitsAndPicksUpNewOnes() throws Exception {
        try (Git git = Git.init().setDirectory(repo.toFile()).setInitialBranch("main").call()) {
            for (int i = 1; i <= HistoryPane.LIMIT + 5; i++) {
                commit(git, "Commit " + i);
            }
        }

        AtomicReference<HistoryPane> pane = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> {
            pane.set(new HistoryPane(new DefaultGitService(new GitCli())));
            pane.get().showRepository(Optional.of(repo));
        });

        List<String> messages = await(pane.get(), m -> m.size() == HistoryPane.LIMIT);
        assertThat(messages).hasSize(HistoryPane.LIMIT);
        assertThat(messages.get(0)).isEqualTo("Commit " + (HistoryPane.LIMIT + 5));
        assertThat(messages.get(HistoryPane.LIMIT - 1)).isEqualTo("Commit 6");

        try (Git git = Git.open(repo.toFile())) {
            commit(git, "Newest");
        }
        SwingUtilities.invokeAndWait(() -> pane.get().refresh());

        assertThat(await(pane.get(), m -> !m.isEmpty() && m.get(0).equals("Newest")).get(0)).isEqualTo("Newest");
    }

    @Test
    void selectedCommitIsTheSelectedRow() throws Exception {
        try (Git git = Git.init().setDirectory(repo.toFile()).setInitialBranch("main").call()) {
            commit(git, "First");
            commit(git, "Second");
        }
        AtomicReference<HistoryPane> pane = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> {
            pane.set(new HistoryPane(new DefaultGitService(new GitCli())));
            pane.get().showRepository(Optional.of(repo));
        });
        await(pane.get(), m -> m.size() == 2);

        SwingUtilities.invokeAndWait(() -> {
            assertThat(pane.get().selectedCommit()).isEmpty();
            pane.get().selectRowForTest(1);
            assertThat(pane.get().selectedCommit()).map(c -> c.message()).contains("First");
        });
    }

    @Test
    void commitAskedForWhileLoadingIsSelectedOnceRead() throws Exception {
        String first;
        try (Git git = Git.init().setDirectory(repo.toFile()).setInitialBranch("main").call()) {
            commit(git, "First");
            first = git.log().call().iterator().next().getName();
            commit(git, "Second");
        }
        AtomicReference<HistoryPane> pane = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> {
            pane.set(new HistoryPane(new DefaultGitService(new GitCli())));
            pane.get().showRepository(Optional.of(repo));
            pane.get().selectCommit(first); // nothing read yet
        });
        await(pane.get(), m -> m.size() == 2);

        SwingUtilities.invokeAndWait(() ->
                assertThat(pane.get().selectedCommit()).map(c -> c.message()).contains("First"));
    }

    @Test
    void fullHistoryShowsAllCommitsInPortionsAndSelectsOneFromALaterPortion() throws Exception {
        int total = HistoryStreamLoader.FIRST_PORTION + 120;
        String oldest;
        try (Git git = Git.init().setDirectory(repo.toFile()).setInitialBranch("main").call()) {
            commit(git, "Commit 1");
            oldest = git.log().call().iterator().next().getName();
            for (int i = 2; i <= total; i++) {
                commit(git, "Commit " + i);
            }
        }

        AtomicReference<HistoryPane> pane = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> {
            pane.set(HistoryPane.fullHistory(new DefaultGitService(new GitCli())));
            pane.get().showRepository(Optional.of(repo));
            pane.get().selectCommit(oldest); // in the second portion
        });

        List<String> messages = await(pane.get(), m -> m.size() == total);
        assertThat(messages).hasSize(total);
        assertThat(messages.get(0)).isEqualTo("Commit " + total);
        assertThat(messages.get(total - 1)).isEqualTo("Commit 1");
        SwingUtilities.invokeAndWait(() -> {
            assertThat(pane.get().isComplete()).isTrue();
            assertThat(pane.get().selectedCommit()).map(c -> c.id()).contains(oldest);
        });

        // a refresh with a new commit replaces the rows at once and keeps the selection
        try (Git git = Git.open(repo.toFile())) {
            commit(git, "Newest");
        }
        SwingUtilities.invokeAndWait(() -> pane.get().refresh());
        messages = await(pane.get(), m -> m.size() == total + 1);
        assertThat(messages.get(0)).isEqualTo("Newest");
        SwingUtilities.invokeAndWait(() ->
                assertThat(pane.get().selectedCommit()).map(c -> c.id()).contains(oldest));
    }

    @Test
    void fullHistoryIsReadAgainOnlyWhenRefsChangeAndKeepsTheSelectionMeanwhile() throws Exception {
        try (Git git = Git.init().setDirectory(repo.toFile()).setInitialBranch("main").call()) {
            commit(git, "First");
            commit(git, "Second");
        }
        java.util.concurrent.atomic.AtomicInteger opened = new java.util.concurrent.atomic.AtomicInteger();
        GitService real = new DefaultGitService(new GitCli());
        GitService counting = (GitService) java.lang.reflect.Proxy.newProxyInstance(
                GitService.class.getClassLoader(), new Class<?>[] {GitService.class}, (proxy, method, args) -> {
                    if (method.getName().equals("openHistory")) {
                        opened.incrementAndGet();
                    }
                    try {
                        return method.invoke(real, args);
                    } catch (java.lang.reflect.InvocationTargetException e) {
                        throw e.getCause();
                    }
                });
        List<Optional<CommitInfo>> notified = new java.util.concurrent.CopyOnWriteArrayList<>();
        AtomicReference<HistoryPane> pane = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> {
            pane.set(HistoryPane.fullHistory(counting));
            pane.get().addSelectionListener(notified::add);
            pane.get().showRepository(Optional.of(repo));
        });
        await(pane.get(), m -> m.size() == 2);
        awaitIdle(pane.get());
        SwingUtilities.invokeAndWait(() -> pane.get().selectRowForTest(1)); // "First"
        assertThat(opened.get()).isEqualTo(1);

        // only files changed: the history is not read again
        Files.writeString(repo.resolve("a.txt"), "edited", StandardCharsets.UTF_8);
        SwingUtilities.invokeAndWait(() -> pane.get().refresh());
        awaitIdle(pane.get());
        assertThat(opened.get()).isEqualTo(1);

        // a new commit: read again, and the selected commit stays selected without passing through "none"
        notified.clear();
        try (Git git = Git.open(repo.toFile())) {
            commit(git, "Third");
        }
        SwingUtilities.invokeAndWait(() -> pane.get().refresh());
        await(pane.get(), m -> m.size() == 3);
        awaitIdle(pane.get());
        assertThat(opened.get()).isEqualTo(2);
        SwingUtilities.invokeAndWait(() ->
                assertThat(pane.get().selectedCommit()).map(CommitInfo::message).contains("First"));
        assertThat(notified).noneMatch(Optional::isEmpty);
    }

    @Test
    void filterShowsMatchingCommitsAndKeepsTheSelectionIfItMatches() throws Exception {
        try (Git git = Git.init().setDirectory(repo.toFile()).setInitialBranch("main").call()) {
            commit(git, "Add login form");
            commit(git, "Update README");
            commit(git, "Fix login bug");
        }
        List<Optional<CommitInfo>> notified = new java.util.concurrent.CopyOnWriteArrayList<>();
        AtomicReference<HistoryPane> pane = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> {
            pane.set(HistoryPane.fullHistory(new DefaultGitService(new GitCli())));
            pane.get().addSelectionListener(notified::add);
            pane.get().showRepository(Optional.of(repo));
        });
        await(pane.get(), m -> m.size() == 3);
        awaitIdle(pane.get());

        SwingUtilities.invokeAndWait(() -> {
            pane.get().selectRowForTest(0); // "Fix login bug"
            notified.clear();
            pane.get().setFilterText("login");
            assertThat(pane.get().shownMessages()).containsExactly("Fix login bug", "Add login form");
            assertThat(pane.get().selectedCommit()).map(CommitInfo::message).contains("Fix login bug");
            assertThat(notified).isEmpty(); // the selected commit did not change

            pane.get().setFilterText("readme"); // the selected commit is filtered out
            assertThat(pane.get().shownMessages()).containsExactly("Update README");
            assertThat(pane.get().selectedCommit()).isEmpty();
            assertThat(notified).containsExactly(Optional.empty());

            pane.get().setFilterText("");
            assertThat(pane.get().shownMessages()).hasSize(3);

            // Esc in the filter clears it; with an empty filter the key goes on (the window closes)
            javax.swing.Action escape = pane.get().filterField().getActionMap().get(
                    pane.get().filterField().getInputMap(javax.swing.JComponent.WHEN_FOCUSED)
                            .get(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_ESCAPE, 0)));
            pane.get().setFilterText("login");
            assertThat(escape.isEnabled()).isTrue();
            escape.actionPerformed(null);
            assertThat(pane.get().filterField().getText()).isEmpty();
            assertThat(pane.get().shownMessages()).hasSize(3);
            assertThat(escape.isEnabled()).isFalse();
        });
    }

    private static void awaitIdle(HistoryPane pane) throws Exception {
        AtomicReference<Boolean> reading = new AtomicReference<>(true);
        long deadline = System.currentTimeMillis() + 10_000;
        while (reading.get() && System.currentTimeMillis() < deadline) {
            Thread.sleep(20);
            SwingUtilities.invokeAndWait(() -> reading.set(pane.isReading()));
        }
        SwingUtilities.invokeAndWait(() -> { }); // let the last results reach the pane
    }

    @Test
    void datesAreRelativeForTodayAndYesterday() {
        Clock clock = Clock.fixed(Instant.parse("2026-11-14T12:00:00Z"), ZoneId.of("UTC"));
        CommitDates dates = new CommitDates(clock, Locale.UK);

        assertThat(dates.format(Instant.parse("2026-11-14T09:05:00Z"))).isEqualTo("Today 09:05");
        assertThat(dates.format(Instant.parse("2026-11-13T23:59:00Z"))).isEqualTo("Yesterday 23:59");
        assertThat(dates.format(Instant.parse("2026-11-01T08:30:00Z"))).isEqualTo("01/11/2026, 08:30");
    }

    private static List<String> await(HistoryPane pane, Predicate<List<String>> done)
            throws Exception {
        AtomicReference<List<String>> messages = new AtomicReference<>();
        long deadline = System.currentTimeMillis() + 10_000;
        do {
            SwingUtilities.invokeAndWait(() -> messages.set(pane.messages()));
            if (done.test(messages.get())) {
                return messages.get();
            }
            Thread.sleep(50);
        } while (System.currentTimeMillis() < deadline);
        return messages.get();
    }

    private void commit(Git git, String message) throws Exception {
        Files.writeString(repo.resolve("a.txt"), message, StandardCharsets.UTF_8);
        git.add().addFilepattern("a.txt").call();
        git.commit().setMessage(message).setAuthor(IDENT).setCommitter(IDENT).setSign(false).call();
    }
}
