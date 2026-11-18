package cz.wingit.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.StoredConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import cz.wingit.core.cli.GitCli;
import cz.wingit.core.log.CommitInfo;

class FetchTest {

    @TempDir
    Path tempDir;

    private final GitCli cli = new GitCli();
    private final GitService git = new DefaultGitService(cli);
    /** Commits get increasing dates, so the history order does not depend on commits made within a second. */
    private Instant nextDate = Instant.parse("2025-01-01T10:00:00Z");

    @Test
    void fetchedCommitsAreCountedAndShownAsUnpulledUntilPulled() throws Exception {
        Path server = tempDir.resolve("server.git");
        cli.run(tempDir, List.of("init", "--bare", "-q", "--initial-branch=main", server.toString()));
        Path mine = createRepo("mine", server);
        commit(mine, "a.txt", "first");
        git.push(mine, null, line -> { });
        Path theirs = tempDir.resolve("theirs");
        cli.run(tempDir, List.of("clone", "-q", server.toString(), theirs.toString()));
        configure(theirs);
        String second = commit(theirs, "b.txt", "second");
        String third = commit(theirs, "c.txt", "third");
        git.push(theirs, null, line -> { });

        assertThat(git.unpulledCount(mine, 100)).isZero(); // not fetched yet
        assertThat(git.fetchInBackground(mine)).isTrue();
        assertThat(git.fetchInBackground(mine)).isFalse(); // nothing new the second time

        assertThat(git.unpulledCount(mine, 100)).isEqualTo(2);
        assertThat(git.unpulledCount(mine, 1)).isEqualTo(1);
        List<CommitInfo> log = git.recentCommits(mine, 100);
        assertThat(log).extracting(CommitInfo::message).containsExactly("third", "second", "first");
        assertThat(log).extracting(CommitInfo::unpulled).containsExactly(true, true, false);
        assertThat(log).extracting(CommitInfo::id).startsWith(third, second);

        git.pull(mine, null, line -> { });
        assertThat(git.unpulledCount(mine, 100)).isZero();
        assertThat(git.recentCommits(mine, 100)).extracting(CommitInfo::unpulled).containsOnly(false);
    }

    @Test
    void fetchDownloadsNewBranchesAndForgetsDeletedOnes() throws Exception {
        Path server = tempDir.resolve("branches.git");
        cli.run(tempDir, List.of("init", "--bare", "-q", "--initial-branch=main", server.toString()));
        Path mine = createRepo("me", server);
        commit(mine, "a.txt", "first");
        git.push(mine, null, line -> { });
        cli.run(mine, List.of("push", "-q", "origin", "main:refs/heads/old"));
        Path theirs = tempDir.resolve("other");
        cli.run(tempDir, List.of("clone", "-q", server.toString(), theirs.toString()));
        cli.run(theirs, List.of("push", "-q", "origin", "main:refs/heads/feature", ":refs/heads/old"));

        git.fetch(mine, null, line -> { });

        assertThat(git.refs(mine).remotes().get(0).branches())
                .contains("feature")
                .doesNotContain("old");
    }

    @Test
    void repositoryWithoutRemoteCannotFetchButBackgroundFetchSkipsIt() throws Exception {
        Path repo = createRepo("alone", null);
        commit(repo, "a.txt", "first");

        assertThatThrownBy(() -> git.fetch(repo, null, line -> { })).isInstanceOf(GitException.class)
                .hasMessageContaining("no remote");
        assertThat(git.fetchInBackground(repo)).isFalse();
        assertThat(git.unpulledCount(repo, 100)).isZero();
    }

    private String commit(Path repo, String file, String message) throws Exception {
        Files.writeString(repo.resolve(file), message, StandardCharsets.UTF_8);
        nextDate = nextDate.plusSeconds(60);
        git.commit(repo, git.status(repo).changes(), message, nextDate);
        try (Git jgit = Git.open(repo.toFile())) {
            return jgit.getRepository().resolve("HEAD").name();
        }
    }

    private Path createRepo(String name, Path server) throws Exception {
        Path dir = Files.createDirectory(tempDir.resolve(name));
        cli.run(dir, List.of("init", "-q", "--initial-branch=main"));
        configure(dir);
        if (server != null) {
            cli.run(dir, List.of("remote", "add", "origin", server.toString()));
        }
        return dir;
    }

    private static void configure(Path dir) throws Exception {
        try (Git jgit = Git.open(dir.toFile())) {
            StoredConfig config = jgit.getRepository().getConfig();
            config.setString("user", null, "name", "Test");
            config.setString("user", null, "email", "test@example.invalid");
            config.setBoolean("commit", null, "gpgsign", false);
            config.setString("core", null, "hooksPath", "no-hooks");
            config.save();
        }
    }
}
