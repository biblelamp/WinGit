package cz.wingit.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.StoredConfig;
import org.eclipse.jgit.revwalk.RevCommit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import cz.wingit.core.cli.GitCli;
import cz.wingit.core.remote.Credentials;
import cz.wingit.core.status.ChangeType;
import cz.wingit.core.status.FileChange;

class CommitPushPullTest {

    @TempDir
    Path tempDir;

    private final GitCli cli = new GitCli();
    private final GitService git = new DefaultGitService(cli);

    @Test
    void commitsOnlySelectedFilesIncludingNewAndDeleted() throws Exception {
        Path repo = createRepo("commit");
        write(repo, "keep.txt", "one");
        write(repo, "gone.txt", "two");
        write(repo, "other.txt", "three");
        git.commit(repo, git.status(repo).changes(), "initial");

        write(repo, "keep.txt", "changed");
        Files.delete(repo.resolve("gone.txt"));
        write(repo, "new [1].txt", "new");   // brackets would be glob magic without literal pathspecs
        write(repo, "other.txt", "not committed");
        List<FileChange> selected = git.status(repo).changes().stream()
                .filter(c -> !c.path().equals("other.txt"))
                .toList();

        git.commit(repo, selected, "Second\n\nŽluťoučký kůň");

        assertThat(git.status(repo).changes())
                .containsExactly(new FileChange("other.txt", null, ChangeType.MODIFIED));
        RevCommit head = headCommit(repo);
        assertThat(head.getFullMessage().strip()).isEqualTo("Second\n\nŽluťoučký kůň");
        assertThat(git.recentCommits(repo, 10)).hasSize(2);
    }

    @Test
    void commitsStagedDeletionAndLeavesOtherStagedFilesStaged() throws Exception {
        Path repo = createRepo("staged");
        write(repo, "a.txt", "a");
        write(repo, "b.txt", "b");
        git.commit(repo, git.status(repo).changes(), "initial");
        cli.run(repo, List.of("rm", "-q", "a.txt"));
        write(repo, "b.txt", "b2");
        cli.run(repo, List.of("add", "b.txt"));

        git.commit(repo, List.of(new FileChange("a.txt", ChangeType.DELETED, null)), "remove a");

        assertThat(git.status(repo).changes())
                .containsExactly(new FileChange("b.txt", ChangeType.MODIFIED, null));
        assertThat(headCommit(repo).getShortMessage()).isEqualTo("remove a");
    }

    @Test
    void commitWithDateSetsAuthorAndCommitterTime() throws Exception {
        Path repo = createRepo("backdated");
        write(repo, "a.txt", "a");
        Instant date = Instant.parse("2024-03-01T08:30:00Z");

        git.commit(repo, git.status(repo).changes(), "old work", date);

        RevCommit head = headCommit(repo);
        assertThat(head.getAuthorIdent().getWhenAsInstant()).isEqualTo(date);
        assertThat(head.getCommitterIdent().getWhenAsInstant()).isEqualTo(date);
    }

    @Test
    void savedCommitDateIsRememberedPerRepositoryAndCanBeForgotten() throws Exception {
        Path repo = createRepo("saved-date");
        Path other = createRepo("other-repo");
        Instant date = Instant.parse("2024-03-01T08:30:00Z");
        assertThat(git.savedCommitDate(repo)).isEmpty();
        git.setSavedCommitDate(repo, null); // nothing to forget: no error

        git.setSavedCommitDate(repo, date);

        assertThat(git.savedCommitDate(repo)).contains(date);
        assertThat(git.savedCommitDate(other)).isEmpty();

        git.setSavedCommitDate(repo, null);

        assertThat(git.savedCommitDate(repo)).isEmpty();
    }

    @Test
    void unreadableSavedCommitDateIsIgnored() throws Exception {
        Path repo = createRepo("bad-date");
        cli.run(repo, List.of("config", "--local", "wingit.commitDate", "yesterday"));

        assertThat(git.savedCommitDate(repo)).isEmpty();
    }

    @Test
    void gitDateUsesInternalFormatWithOffset() {
        Instant date = Instant.parse("2024-03-01T08:30:00Z");
        assertThat(DefaultGitService.gitDate(date, ZoneId.of("Europe/Prague"))).isEqualTo("1709281800 +0100");
        assertThat(DefaultGitService.gitDate(date, ZoneId.of("America/St_Johns"))).isEqualTo("1709281800 -0330");
    }

    @Test
    void storeCredentialsRemembersUserNameForTheHost() throws Exception {
        Path repo = createRepo("remember");
        // No helper for this repository: the test must never write into the real credential store.
        cli.run(repo, List.of("config", "--local", "credential.helper", ""));

        git.storeCredentials(repo, "https://github.com/someone/project.git", new Credentials("octo", "token"));

        assertThat(cli.run(repo, List.of("config", "--local", "credential.https://github.com.username"))
                .stdout().strip()).isEqualTo("octo");
    }

    @Test
    void credentialOriginKeepsSchemeHostAndPortOnly() {
        assertThat(DefaultGitService.credentialOrigin("https://joe@GitHub.com/a/b.git")).isEqualTo("https://GitHub.com");
        assertThat(DefaultGitService.credentialOrigin("https://git.example:8443/r.git"))
                .isEqualTo("https://git.example:8443");
        assertThat(DefaultGitService.credentialOrigin("git@github.com:a/b.git")).isNull();
        assertThat(DefaultGitService.credentialOrigin("ssh://git@github.com/a/b.git")).isNull();
    }

    @Test
    void firstCommitInEmptyRepository() throws Exception {
        Path repo = createRepo("empty");
        write(repo, "readme.md", "# hi");

        git.commit(repo, git.status(repo).changes(), "first");

        assertThat(git.status(repo).isClean()).isTrue();
        assertThat(headCommit(repo).getShortMessage()).isEqualTo("first");
    }

    @Test
    void pushWithoutUpstreamSetsItAndPullBringsChangesBack() throws Exception {
        Path remote = tempDir.resolve("remote.git");
        cli.run(tempDir, List.of("init", "--bare", "-q", remote.toString()));
        Path first = createRepo("first");
        cli.run(first, List.of("remote", "add", "origin", remote.toString()));
        write(first, "file.txt", "v1");
        git.commit(first, git.status(first).changes(), "v1");
        String branch = git.currentBranch(first).orElseThrow();

        git.push(first, null, line -> { });

        assertThat(git.refs(first).localBranches()).anySatisfy(b -> {
            assertThat(b.name()).isEqualTo(branch);
            assertThat(b.upstream()).isEqualTo("origin/" + branch);
        });

        Path second = tempDir.resolve("second");
        git.clone(remote.toString(), second, line -> { });
        configureIdentity(second);
        write(second, "file.txt", "v2");
        git.commit(second, git.status(second).changes(), "v2");
        git.push(second, null, line -> { });

        git.pull(first, null, line -> { });

        assertThat(first.resolve("file.txt")).hasContent("v2");
        assertThat(headCommit(first).getShortMessage()).isEqualTo("v2");
    }

    @Test
    void deleteRemoteBranchRemovesItOnServerOnly() throws Exception {
        Path remote = tempDir.resolve("server.git");
        cli.run(tempDir, List.of("init", "--bare", "-q", remote.toString()));
        Path repo = createRepo("pusher");
        cli.run(repo, List.of("remote", "add", "origin", remote.toString()));
        write(repo, "f.txt", "1");
        git.commit(repo, git.status(repo).changes(), "1");
        git.push(repo, null, line -> { });
        git.createBranch(repo, "feature", true);
        git.push(repo, null, line -> { });
        assertThat(cli.run(remote, List.of("branch", "--list", "feature")).stdout()).contains("feature");

        git.deleteRemoteBranch(repo, "origin", "feature", null, line -> { });

        assertThat(cli.run(remote, List.of("branch", "--list", "feature")).stdout()).isBlank();
        assertThat(git.refs(repo).remotes().get(0).branches()).doesNotContain("feature");
        assertThat(git.currentBranch(repo)).contains("feature"); // the local branch stays
    }

    @Test
    void renameRemoteBranchMovesItAndKeepsLocalTracking() throws Exception {
        Path remote = tempDir.resolve("rename.git");
        cli.run(tempDir, List.of("init", "--bare", "-q", remote.toString()));
        Path repo = createRepo("renamer");
        cli.run(repo, List.of("remote", "add", "origin", remote.toString()));
        write(repo, "f.txt", "1");
        git.commit(repo, git.status(repo).changes(), "1");
        git.push(repo, null, line -> { });
        git.createBranch(repo, "old-name", true);
        git.push(repo, null, line -> { });

        git.renameRemoteBranch(repo, "origin", "old-name", "new-name", null, line -> { });

        String serverBranches = cli.run(remote, List.of("branch", "--list")).stdout();
        assertThat(serverBranches).contains("new-name").doesNotContain("old-name");
        assertThat(git.refs(repo).localBranches()).anySatisfy(b -> {
            assertThat(b.name()).isEqualTo("old-name"); // the local name is kept
            assertThat(b.upstream()).isEqualTo("origin/new-name");
        });
    }

    @Test
    void renameRemoteBranchRefusesWhenServerBranchMoved() throws Exception {
        Path remote = tempDir.resolve("moved.git");
        cli.run(tempDir, List.of("init", "--bare", "-q", remote.toString()));
        Path repo = createRepo("stale");
        cli.run(repo, List.of("remote", "add", "origin", remote.toString()));
        write(repo, "f.txt", "1");
        git.commit(repo, git.status(repo).changes(), "1");
        git.createBranch(repo, "topic", true);
        git.push(repo, null, line -> { });
        // someone else pushes to topic; this clone has not fetched it
        Path other = tempDir.resolve("other");
        git.clone(remote.toString(), other, line -> { });
        configureIdentity(other);
        git.switchToNewTrackingBranch(other, "origin", "topic");
        write(other, "g.txt", "2");
        git.commit(other, git.status(other).changes(), "2");
        git.push(other, null, line -> { });

        assertThatThrownBy(() -> git.renameRemoteBranch(repo, "origin", "topic", "renamed", null, line -> { }))
                .isInstanceOf(GitCommandException.class);
        assertThat(cli.run(remote, List.of("branch", "--list")).stdout()).contains("topic").doesNotContain("renamed");
    }

    @Test
    void pullWithoutUpstreamFailsWithClearMessage() throws Exception {
        Path repo = createRepo("lonely");
        write(repo, "x.txt", "x");
        git.commit(repo, git.status(repo).changes(), "x");

        assertThatThrownBy(() -> git.pull(repo, null, line -> { }))
                .isInstanceOf(GitException.class)
                .hasMessageContaining("does not track a remote branch");
    }

    @Test
    void pushWithoutRemoteFailsWithClearMessage() throws Exception {
        Path repo = createRepo("noremote");
        write(repo, "x.txt", "x");
        git.commit(repo, git.status(repo).changes(), "x");

        assertThatThrownBy(() -> git.push(repo, null, line -> { }))
                .isInstanceOf(GitException.class)
                .hasMessageContaining("no remote");
    }

    private Path createRepo(String name) throws Exception {
        Path dir = Files.createDirectory(tempDir.resolve(name));
        git.init(dir);
        configureIdentity(dir);
        return dir;
    }

    private static void configureIdentity(Path repo) throws Exception {
        try (Git jgit = Git.open(repo.toFile())) {
            StoredConfig config = jgit.getRepository().getConfig();
            config.setString("user", null, "name", "Test");
            config.setString("user", null, "email", "test@example.invalid");
            config.setBoolean("commit", null, "gpgsign", false);
            config.setString("core", null, "hooksPath", "no-hooks");
            config.save();
        }
    }

    private static RevCommit headCommit(Path repo) throws Exception {
        try (Git jgit = Git.open(repo.toFile())) {
            Repository repository = jgit.getRepository();
            return jgit.log().add(repository.resolve("HEAD")).setMaxCount(1).call().iterator().next();
        }
    }

    private static void write(Path repo, String path, String text) throws Exception {
        Files.writeString(repo.resolve(path), text, StandardCharsets.UTF_8);
    }
}
