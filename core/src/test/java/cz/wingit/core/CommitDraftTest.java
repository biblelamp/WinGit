package cz.wingit.core;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;

import org.eclipse.jgit.api.Git;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import cz.wingit.core.cli.GitCli;

class CommitDraftTest {

    @TempDir
    Path repo;

    @Test
    void draftIsKeptInTheGitDirectoryUntilCleared() throws Exception {
        Git.init().setDirectory(repo.toFile()).setInitialBranch("main").call().close();
        GitService git = new DefaultGitService(new GitCli());

        assertThat(git.commitDraft(repo)).isEmpty();

        git.setCommitDraft(repo, "Fix typo\n\nČeský popis ✓");
        assertThat(git.commitDraft(repo)).contains("Fix typo\n\nČeský popis ✓");
        assertThat(repo.resolve(".git").resolve(DefaultGitService.COMMIT_DRAFT_FILE)).exists();

        git.setCommitDraft(repo, "  ");
        assertThat(git.commitDraft(repo)).isEmpty();
        assertThat(Files.exists(repo.resolve(".git").resolve(DefaultGitService.COMMIT_DRAFT_FILE))).isFalse();
        git.setCommitDraft(repo, null); // nothing to remove: no error
    }
}
