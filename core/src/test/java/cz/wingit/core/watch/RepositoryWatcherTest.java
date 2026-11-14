package cz.wingit.core.watch;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.PersonIdent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RepositoryWatcherTest {

    private static final Duration DEBOUNCE = Duration.ofMillis(150);
    /** Long enough for the OS to deliver events and the debounce to fire. */
    private static final long SETTLE_MILLIS = 1500;

    @TempDir
    Path repo;

    private final AtomicInteger changes = new AtomicInteger();
    private RepositoryWatch watch;

    @BeforeEach
    void createRepository() throws Exception {
        PersonIdent ident = new PersonIdent("Test", "test@example.invalid");
        try (Git git = Git.init().setDirectory(repo.toFile()).call()) {
            write(".gitignore", "*.log\ntarget/\n");
            write("a.txt", "a\n");
            git.add().addFilepattern(".").call();
            git.commit().setMessage("init").setAuthor(ident).setCommitter(ident).setSign(false).call();
        }
        Files.createDirectories(repo.resolve("target/classes"));
        watch = RepositoryWatches.start(repo, DEBOUNCE, changes::incrementAndGet);
        Thread.sleep(200); // let the watch settle
    }

    @AfterEach
    void stop() {
        watch.close();
    }

    @Test
    void burstOfEditsIsReportedOnce() throws Exception {
        for (int i = 0; i < 20; i++) {
            write("a.txt", "a" + i + "\n");
        }
        write("src/New.java", "class New {}\n");

        Thread.sleep(SETTLE_MILLIS);
        assertThat(changes.get()).isEqualTo(1);
    }

    @Test
    void ignoredFilesAndGitObjectsAreNotReported() throws Exception {
        write("build.log", "x\n");
        write("target/classes/A.class", "x\n");
        write(".git/objects/ab/dummy", "x\n");

        Thread.sleep(SETTLE_MILLIS);
        assertThat(changes.get()).isZero();
    }

    @Test
    void stagingFromOutsideIsReported() throws Exception {
        write("a.txt", "changed\n");
        Thread.sleep(SETTLE_MILLIS);
        changes.set(0);

        try (Git git = Git.open(repo.toFile())) {
            git.add().addFilepattern("a.txt").call(); // only .git/index changes
        }

        Thread.sleep(SETTLE_MILLIS);
        assertThat(changes.get()).isEqualTo(1);
    }

    @Test
    void closedWatchReportsNothing() throws Exception {
        watch.close();

        write("a.txt", "after close\n");

        Thread.sleep(SETTLE_MILLIS);
        assertThat(changes.get()).isZero();
    }

    @Test
    void relevantGitPaths() {
        assertThat(RepositoryWatcher.isRelevant("src/A.java")).isTrue();
        assertThat(RepositoryWatcher.isRelevant(".gitignore")).isTrue();
        assertThat(RepositoryWatcher.isRelevant(".git/index")).isTrue();
        assertThat(RepositoryWatcher.isRelevant(".git/HEAD")).isTrue();
        assertThat(RepositoryWatcher.isRelevant(".git/refs/heads/main")).isTrue();
        assertThat(RepositoryWatcher.isRelevant(".git")).isFalse();
        assertThat(RepositoryWatcher.isRelevant(".git/index.lock")).isFalse();
        assertThat(RepositoryWatcher.isRelevant(".git/objects/ab/cdef")).isFalse();
        assertThat(RepositoryWatcher.isRelevant(".git/logs/HEAD")).isFalse();
        assertThat(RepositoryWatcher.isRelevant(".git/FETCH_HEAD")).isFalse();
    }

    private void write(String relative, String content) throws Exception {
        Path file = repo.resolve(relative);
        Files.createDirectories(file.getParent());
        Files.writeString(file, content, StandardCharsets.UTF_8);
    }
}
