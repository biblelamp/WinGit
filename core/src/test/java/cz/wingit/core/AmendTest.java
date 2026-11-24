package cz.wingit.core;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.StoredConfig;
import org.eclipse.jgit.revwalk.RevCommit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import cz.wingit.core.cli.GitCli;
import cz.wingit.core.status.ChangeType;
import cz.wingit.core.status.FileChange;

class AmendTest {

    @TempDir
    Path tempDir;

    private final GitCli cli = new GitCli();
    private final GitService git = new DefaultGitService(cli);

    @Test
    void amendIsPossibleOnlyForAnUnpushedLastCommit() throws Exception {
        Path repo = createRepo("repo");
        assertThat(git.amendCheck(repo).state()).isEqualTo(AmendCheck.State.NO_COMMIT);

        write(repo, "a.txt", "a");
        git.commit(repo, git.status(repo).changes(), "First\n\nBody ✓");
        assertThat(git.amendCheck(repo)).isEqualTo(new AmendCheck(AmendCheck.State.POSSIBLE, "First\n\nBody ✓\n"));

        Path remote = tempDir.resolve("remote.git");
        cli.run(tempDir, List.of("init", "--bare", "-q", remote.toString()));
        cli.run(repo, List.of("remote", "add", "origin", remote.toString()));
        git.push(repo, null, line -> { });
        assertThat(git.amendCheck(repo).state()).isEqualTo(AmendCheck.State.PUSHED);

        write(repo, "a.txt", "a2");
        git.commit(repo, git.status(repo).changes(), "Second");
        assertThat(git.amendCheck(repo).possible()).isTrue();
    }

    @Test
    void amendReplacesTheLastCommitWithTheSelectedFilesAndKeepsItsAuthorDate() throws Exception {
        Path repo = createRepo("files");
        write(repo, "a.txt", "a");
        Instant date = Instant.parse("2024-03-01T08:30:00Z");
        git.commit(repo, git.status(repo).changes(), "first", date);
        RevCommit before = headCommit(repo);

        write(repo, "a.txt", "a2");
        write(repo, "b.txt", "not committed");
        List<FileChange> selected = git.status(repo).changes().stream()
                .filter(c -> c.path().equals("a.txt"))
                .toList();
        git.commit(repo, selected, "first, fixed", null, true);

        RevCommit after = headCommit(repo);
        assertThat(after.getParentCount()).isZero();
        assertThat(after.getFullMessage().strip()).isEqualTo("first, fixed");
        assertThat(after.getAuthorIdent().getWhenAsInstant()).isEqualTo(date);
        assertThat(after.getId()).isNotEqualTo(before.getId());
        assertThat(git.status(repo).changes())
                .containsExactly(new FileChange("b.txt", null, ChangeType.UNTRACKED));
    }

    @Test
    void amendWithoutFilesChangesOnlyTheMessageAndDateAndLeavesStagedChanges() throws Exception {
        Path repo = createRepo("message");
        write(repo, "a.txt", "a");
        git.commit(repo, git.status(repo).changes(), "typo");
        write(repo, "a.txt", "staged");
        cli.run(repo, List.of("add", "a.txt"));
        Instant date = Instant.parse("2023-12-24T18:00:00Z");

        git.commit(repo, List.of(), "No typo", date, true);

        RevCommit head = headCommit(repo);
        assertThat(head.getFullMessage().strip()).isEqualTo("No typo");
        assertThat(head.getAuthorIdent().getWhenAsInstant()).isEqualTo(date);
        assertThat(head.getCommitterIdent().getWhenAsInstant()).isEqualTo(date);
        assertThat(git.recentCommits(repo, 10)).hasSize(1);
        assertThat(git.status(repo).changes())
                .containsExactly(new FileChange("a.txt", ChangeType.MODIFIED, null));
    }

    private Path createRepo(String name) throws Exception {
        Path dir = Files.createDirectory(tempDir.resolve(name));
        git.init(dir);
        try (Git jgit = Git.open(dir.toFile())) {
            StoredConfig config = jgit.getRepository().getConfig();
            config.setString("user", null, "name", "Test");
            config.setString("user", null, "email", "test@example.invalid");
            config.setBoolean("commit", null, "gpgsign", false);
            config.setString("core", null, "hooksPath", "no-hooks");
            config.save();
        }
        return dir;
    }

    private static RevCommit headCommit(Path repo) throws Exception {
        try (Git jgit = Git.open(repo.toFile())) {
            return jgit.log().add(jgit.getRepository().resolve("HEAD")).setMaxCount(1).call().iterator().next();
        }
    }

    private static void write(Path repo, String path, String text) throws Exception {
        Files.writeString(repo.resolve(path), text, StandardCharsets.UTF_8);
    }
}
