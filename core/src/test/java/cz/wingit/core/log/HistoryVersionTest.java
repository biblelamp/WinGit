package cz.wingit.core.log;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.RefUpdate;
import org.eclipse.jgit.lib.StoredConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import cz.wingit.core.DefaultGitService;
import cz.wingit.core.GitService;
import cz.wingit.core.cli.GitCli;

class HistoryVersionTest {

    private static final PersonIdent IDENT = new PersonIdent("Test", "test@example.invalid");

    @TempDir
    Path repo;

    private final GitService git = new DefaultGitService(new GitCli());

    @Test
    void changesWithRefsButNotWithFilesOrStashes() throws Exception {
        try (Git jgit = Git.init().setDirectory(repo.toFile()).setInitialBranch("main").call()) {
            String empty = git.historyVersion(repo);
            commit(jgit, "first");
            String v1 = git.historyVersion(repo);
            assertThat(v1).isNotEqualTo(empty);

            // working tree changes and stashes are not part of the history
            Files.writeString(repo.resolve("a.txt"), "edited", StandardCharsets.UTF_8);
            assertThat(git.historyVersion(repo)).isEqualTo(v1);
            jgit.stashCreate().setPerson(IDENT).call();
            assertThat(git.historyVersion(repo)).isEqualTo(v1);

            commit(jgit, "second");
            String v2 = git.historyVersion(repo);
            assertThat(v2).isNotEqualTo(v1);

            jgit.tag().setName("v1").setAnnotated(false).call();
            String v3 = git.historyVersion(repo);
            assertThat(v3).isNotEqualTo(v2);

            jgit.branchCreate().setName("feature").call();
            String v4 = git.historyVersion(repo);
            assertThat(v4).isNotEqualTo(v3);
            jgit.checkout().setName("feature").call(); // same commit, HEAD points elsewhere
            String v5 = git.historyVersion(repo);
            assertThat(v5).isNotEqualTo(v4);

            RefUpdate update = jgit.getRepository().updateRef("refs/remotes/origin/feature");
            update.setNewObjectId(jgit.getRepository().resolve("HEAD"));
            update.forceUpdate(); // e.g. a fetch: changes the unpushed marks
            String v6 = git.historyVersion(repo);
            assertThat(v6).isNotEqualTo(v5);

            StoredConfig config = jgit.getRepository().getConfig();
            config.setString("branch", "feature", "remote", "origin");
            config.setString("branch", "feature", "merge", "refs/heads/feature");
            config.setString("remote", "origin", "fetch", "+refs/heads/*:refs/remotes/origin/*");
            config.save(); // a new upstream changes the unpulled commits
            assertThat(git.historyVersion(repo)).isNotEqualTo(v6);
        }
    }

    private void commit(Git jgit, String message) throws Exception {
        Files.writeString(repo.resolve("a.txt"), message, StandardCharsets.UTF_8);
        jgit.add().addFilepattern("a.txt").call();
        jgit.commit().setMessage(message).setAuthor(IDENT).setCommitter(IDENT).setSign(false).call();
    }
}
