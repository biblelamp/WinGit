package cz.wingit.core.merge;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.PersonIdent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import cz.wingit.core.DefaultGitService;
import cz.wingit.core.GitException;
import cz.wingit.core.GitService;
import cz.wingit.core.cli.GitCli;
import cz.wingit.core.log.CommitInfo;

class MergePreviewTest {

    @TempDir
    Path repo;

    private final GitService git = new DefaultGitService(new GitCli());
    private final PersonIdent ident = new PersonIdent("Test", "test@example.invalid");

    @Test
    void branchAheadOnlyFastForwards() throws Exception {
        try (Git jgit = init()) {
            commit(jgit, "a.txt", "1", "base");
            jgit.branchCreate().setName("feature").call();
            jgit.checkout().setName("feature").call();
            commit(jgit, "b.txt", "1", "feature 1");
            commit(jgit, "c.txt", "1", "feature 2");
            jgit.checkout().setName("main").call();
        }

        MergePreview preview = git.mergePreview(repo, "feature", 10);

        assertThat(preview.kind()).isEqualTo(MergePreview.Kind.FAST_FORWARD);
        assertThat(preview.incoming()).extracting(CommitInfo::message).containsExactly("feature 2", "feature 1");
        assertThat(preview.incomingCount()).isEqualTo(2);
        assertThat(preview.currentOnly()).isZero();
        assertThat(preview.changedFiles()).isEqualTo(2);
        assertThat(preview.localChanges()).isZero();
    }

    @Test
    void divergedBranchesNeedMergeCommitAndListIsLimited() throws Exception {
        try (Git jgit = init()) {
            commit(jgit, "a.txt", "1", "base");
            jgit.branchCreate().setName("feature").call();
            commit(jgit, "main.txt", "1", "main only");
            jgit.checkout().setName("feature").call();
            commit(jgit, "a.txt", "2", "feature 1");
            commit(jgit, "a.txt", "3", "feature 2");
            commit(jgit, "b.txt", "1", "feature 3");
            jgit.checkout().setName("main").call();
        }

        MergePreview preview = git.mergePreview(repo, "feature", 2);

        assertThat(preview.kind()).isEqualTo(MergePreview.Kind.MERGE_COMMIT);
        assertThat(preview.incoming()).extracting(CommitInfo::message).containsExactly("feature 3", "feature 2");
        assertThat(preview.incomingCount()).isEqualTo(3);
        assertThat(preview.currentOnly()).isEqualTo(1);
        assertThat(preview.changedFiles()).isEqualTo(2); // a.txt and b.txt, not main.txt
    }

    @Test
    void containedBranchIsUpToDate() throws Exception {
        try (Git jgit = init()) {
            commit(jgit, "a.txt", "1", "base");
            jgit.branchCreate().setName("old").call();
            commit(jgit, "a.txt", "2", "newer");
        }

        MergePreview preview = git.mergePreview(repo, "old", 10);

        assertThat(preview.kind()).isEqualTo(MergePreview.Kind.UP_TO_DATE);
        assertThat(preview.incoming()).isEmpty();
        assertThat(preview.currentOnly()).isEqualTo(1);
        assertThat(preview.changedFiles()).isZero();
    }

    @Test
    void branchWithoutCommonCommitIsUnrelated() throws Exception {
        try (Git jgit = init()) {
            commit(jgit, "a.txt", "1", "main");
            jgit.checkout().setOrphan(true).setName("other").call();
            jgit.rm().addFilepattern("a.txt").call();
            commit(jgit, "b.txt", "1", "other");
            jgit.checkout().setName("main").call();
        }

        assertThat(git.mergePreview(repo, "other", 10).kind()).isEqualTo(MergePreview.Kind.UNRELATED);
    }

    @Test
    void countsUncommittedChangesButNotNewFiles() throws Exception {
        try (Git jgit = init()) {
            commit(jgit, "a.txt", "1", "base");
            commit(jgit, "b.txt", "1", "second");
            jgit.branchCreate().setName("feature").call();
        }
        Files.writeString(repo.resolve("a.txt"), "changed", StandardCharsets.UTF_8);
        Files.writeString(repo.resolve("new.txt"), "untracked", StandardCharsets.UTF_8);

        assertThat(git.mergePreview(repo, "feature", 10).localChanges()).isEqualTo(1);
    }

    @Test
    void unknownRefFails() throws Exception {
        try (Git jgit = init()) {
            commit(jgit, "a.txt", "1", "base");
        }

        assertThatThrownBy(() -> git.mergePreview(repo, "missing", 10)).isInstanceOf(GitException.class);
    }

    private Git init() throws Exception {
        return Git.init().setDirectory(repo.toFile()).setInitialBranch("main").call();
    }

    private void commit(Git jgit, String file, String content, String message) throws Exception {
        Files.writeString(repo.resolve(file), content, StandardCharsets.UTF_8);
        jgit.add().addFilepattern(file).call();
        jgit.commit().setMessage(message).setAuthor(ident).setCommitter(ident).setSign(false).call();
    }
}
