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
