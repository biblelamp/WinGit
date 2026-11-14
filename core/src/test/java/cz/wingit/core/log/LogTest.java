package cz.wingit.core.log;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.List;
import java.util.TimeZone;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.PersonIdent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import cz.wingit.core.DefaultGitService;
import cz.wingit.core.GitService;
import cz.wingit.core.cli.GitCli;

class LogTest {

    @TempDir
    Path repo;

    private final GitService git = new DefaultGitService(new GitCli());

    @Test
    void returnsNewestCommitsUpToLimit() throws Exception {
        Instant base = Instant.parse("2026-01-01T10:00:00Z");
        try (Git jgit = Git.init().setDirectory(repo.toFile()).setInitialBranch("main").call()) {
            for (int i = 1; i <= 5; i++) {
                Files.writeString(repo.resolve("a.txt"), "v" + i, StandardCharsets.UTF_8);
                jgit.add().addFilepattern("a.txt").call();
                PersonIdent ident = new PersonIdent(new PersonIdent("Jana Nováková", "jana@example.invalid"),
                        Date.from(base.plusSeconds(60L * i)), TimeZone.getTimeZone(ZoneOffset.UTC));
                jgit.commit().setMessage("Change " + i + "\n\nlonger body").setAuthor(ident).setCommitter(ident)
                        .setSign(false).call();
            }
        }

        List<CommitInfo> commits = git.recentCommits(repo, 3);

        assertThat(commits).extracting(CommitInfo::message).containsExactly("Change 5", "Change 4", "Change 3");
        assertThat(commits.get(0).author()).isEqualTo("Jana Nováková");
        assertThat(commits.get(0).authorTime()).isEqualTo(base.plusSeconds(300));
        assertThat(commits.get(0).shortId()).hasSize(7);
        assertThat(commits.get(0).id()).startsWith(commits.get(0).shortId()).hasSize(40);
    }

    @Test
    void commitsCarryParentsAndRefLabels() throws Exception {
        PersonIdent ident = new PersonIdent("Test", "test@example.invalid");
        try (Git jgit = Git.init().setDirectory(repo.toFile()).setInitialBranch("main").call()) {
            Files.writeString(repo.resolve("a.txt"), "1", StandardCharsets.UTF_8);
            jgit.add().addFilepattern("a.txt").call();
            String first = jgit.commit().setMessage("first").setAuthor(ident).setCommitter(ident).setSign(false)
                    .call().getName();
            jgit.tag().setName("v1.0").setTagger(ident).setMessage("annotated").setSigned(false).call();
            Files.writeString(repo.resolve("a.txt"), "2", StandardCharsets.UTF_8);
            jgit.add().addFilepattern("a.txt").call();
            jgit.commit().setMessage("second").setAuthor(ident).setCommitter(ident).setSign(false).call();
            jgit.branchCreate().setName("feature").call();

            List<CommitInfo> commits = git.recentCommits(repo, 10);

            assertThat(commits.get(0).refs()).containsExactly(
                    new RefLabel("main", RefLabel.Kind.LOCAL_BRANCH, true),
                    new RefLabel("feature", RefLabel.Kind.LOCAL_BRANCH, false));
            assertThat(commits.get(0).parents()).containsExactly(first);
            assertThat(commits.get(1).refs()).containsExactly(new RefLabel("v1.0", RefLabel.Kind.TAG, false));
            assertThat(commits.get(1).parents()).isEmpty();

            jgit.checkout().setName(first).call();
            assertThat(git.recentCommits(repo, 10).get(0).refs()).containsExactly(
                    new RefLabel("HEAD", RefLabel.Kind.DETACHED_HEAD, true),
                    new RefLabel("v1.0", RefLabel.Kind.TAG, false));
        }
    }

    @Test
    void emptyRepositoryHasNoHistory() throws Exception {
        try (Git jgit = Git.init().setDirectory(repo.toFile()).call()) {
            assertThat(git.recentCommits(repo, 50)).isEmpty();
        }
    }
}
