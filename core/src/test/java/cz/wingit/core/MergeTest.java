package cz.wingit.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.StoredConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import cz.wingit.core.cli.GitCli;
import cz.wingit.core.merge.MergeResult;
import cz.wingit.core.merge.MergeResult.Outcome;

class MergeTest {

    @TempDir
    Path repo;

    private final GitService git = new DefaultGitService(new GitCli());
    private String main;

    @Test
    void fastForwardUpToDateAndMergeCommit() throws Exception {
        init();
        git.createBranch(repo, "feature", true);
        commit("f.txt", "feature", "feature work");
        git.switchBranch(repo, main);

        assertThat(git.merge(repo, "feature", false).outcome()).isEqualTo(Outcome.FAST_FORWARD);
        assertThat(repo.resolve("f.txt")).hasContent("feature");
        assertThat(git.merge(repo, "feature", false).outcome()).isEqualTo(Outcome.UP_TO_DATE);

        git.switchBranch(repo, "feature");
        commit("g.txt", "more", "more feature work");
        git.switchBranch(repo, main);
        MergeResult result = git.merge(repo, "feature", true);

        assertThat(result.outcome()).isEqualTo(Outcome.MERGED);
        assertThat(git.recentCommits(repo, 1).get(0).isMerge()).isTrue();
        assertThat(git.mergeInProgress(repo)).isFalse();
    }

    @Test
    void conflictsLeaveMergeInProgressWithMessageAndCanBeAborted() throws Exception {
        init();
        git.createBranch(repo, "feature", true);
        commit("a.txt", "feature version\n", "feature change");
        git.switchBranch(repo, main);
        commit("a.txt", "main version\n", "main change");

        MergeResult result = git.merge(repo, "feature", false);

        assertThat(result.outcome()).isEqualTo(Outcome.CONFLICTS);
        assertThat(result.conflicts()).containsExactly("a.txt");
        assertThat(git.mergeInProgress(repo)).isTrue();
        assertThat(git.mergeMessage(repo)).hasValueSatisfying(m -> {
            assertThat(m).startsWith("Merge branch 'feature'");
            assertThat(m).doesNotContain("#");
        });

        git.abortMerge(repo);

        assertThat(git.mergeInProgress(repo)).isFalse();
        assertThat(git.status(repo).isClean()).isTrue();
        assertThat(repo.resolve("a.txt")).hasContent("main version\n");
        assertThat(git.mergeMessage(repo)).isEmpty();
    }

    @Test
    void resolvedConflictIsCommittedThroughTheCommitDialogPath() throws Exception {
        init();
        git.createBranch(repo, "feature", true);
        commit("a.txt", "feature version\n", "feature change");
        git.switchBranch(repo, main);
        commit("a.txt", "main version\n", "main change");
        git.merge(repo, "feature", false);

        Files.writeString(repo.resolve("a.txt"), "resolved\n", StandardCharsets.UTF_8);
        git.commit(repo, git.status(repo).changes(), git.mergeMessage(repo).orElseThrow());

        assertThat(git.mergeInProgress(repo)).isFalse();
        assertThat(git.recentCommits(repo, 1).get(0).isMerge()).isTrue();
    }

    @Test
    void refusesWhenLocalChangesWouldBeOverwrittenAndRejectsOptionLikeRefs() throws Exception {
        init();
        git.createBranch(repo, "feature", true);
        commit("a.txt", "feature version\n", "feature change");
        git.switchBranch(repo, main);
        Files.writeString(repo.resolve("a.txt"), "local edit\n", StandardCharsets.UTF_8);

        assertThatThrownBy(() -> git.merge(repo, "feature", false)).isInstanceOf(GitCommandException.class);
        assertThat(git.mergeInProgress(repo)).isFalse();
        assertThatThrownBy(() -> git.merge(repo, "--abort", false)).isInstanceOf(IllegalArgumentException.class);
    }

    private void init() throws Exception {
        git.init(repo);
        try (Git jgit = Git.open(repo.toFile())) {
            StoredConfig config = jgit.getRepository().getConfig();
            config.setString("user", null, "name", "Test");
            config.setString("user", null, "email", "test@example.invalid");
            config.setBoolean("commit", null, "gpgsign", false);
            config.setString("core", null, "hooksPath", "no-hooks");
            config.save();
        }
        commit("a.txt", "base\n", "base");
        main = git.currentBranch(repo).orElseThrow();
    }

    private void commit(String path, String content, String message) throws Exception {
        Files.writeString(repo.resolve(path), content, StandardCharsets.UTF_8);
        git.commit(repo, git.status(repo).changes(), message);
    }
}
