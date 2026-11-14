package cz.wingit.core.status;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.MergeResult;
import org.eclipse.jgit.lib.PersonIdent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import cz.wingit.core.DefaultGitService;
import cz.wingit.core.GitException;
import cz.wingit.core.GitService;
import cz.wingit.core.cli.GitCli;

class StatusTest {

    private static final PersonIdent IDENT = new PersonIdent("Test", "test@example.invalid");

    @TempDir
    Path repo;

    private final GitService git = new DefaultGitService(new GitCli());

    @BeforeEach
    void createRepository() throws Exception {
        try (Git jgit = Git.init().setDirectory(repo.toFile()).call()) {
            write(".gitignore", "*.log\n");
            write("a.txt", "a\n");
            write("b.txt", "b\n");
            write("d.txt", "d\n");
            jgit.add().addFilepattern(".").call();
            commit(jgit, "initial");
        }
    }

    @Test
    void freshCommitIsClean() {
        assertThat(git.status(repo).isClean()).isTrue();
    }

    @Test
    void reportsStagedUnstagedAndUntrackedChanges() throws Exception {
        try (Git jgit = Git.open(repo.toFile())) {
            write("a.txt", "a changed\n");                 // unstaged modification
            Files.delete(repo.resolve("b.txt"));          // unstaged deletion
            write("c.txt", "c\n");                         // staged new file
            jgit.add().addFilepattern("c.txt").call();
            write("d.txt", "d staged\n");                  // staged, then changed again
            jgit.add().addFilepattern("d.txt").call();
            write("d.txt", "d staged and changed\n");
            write("dir/čeština.txt", "x\n");               // untracked, non-ASCII name
            write("build.log", "ignored\n");               // ignored
        }

        assertThat(git.status(repo).changes()).containsExactly(
                new FileChange("a.txt", null, ChangeType.MODIFIED),
                new FileChange("b.txt", null, ChangeType.DELETED),
                new FileChange("c.txt", ChangeType.ADDED, null),
                new FileChange("d.txt", ChangeType.MODIFIED, ChangeType.MODIFIED),
                new FileChange("dir/čeština.txt", null, ChangeType.UNTRACKED));
    }

    @Test
    void stagedDeletionIsReported() throws Exception {
        try (Git jgit = Git.open(repo.toFile())) {
            jgit.rm().addFilepattern("b.txt").call();
        }

        assertThat(git.status(repo).changes()).containsExactly(
                new FileChange("b.txt", ChangeType.DELETED, null));
    }

    @Test
    void mergeConflictIsReported() throws Exception {
        try (Git jgit = Git.open(repo.toFile())) {
            String main = jgit.getRepository().getBranch();
            jgit.checkout().setCreateBranch(true).setName("other").call();
            write("a.txt", "other\n");
            jgit.add().addFilepattern("a.txt").call();
            commit(jgit, "other");
            jgit.checkout().setName(main).call();
            write("a.txt", "main\n");
            jgit.add().addFilepattern("a.txt").call();
            commit(jgit, "main");

            MergeResult result = jgit.merge().include(jgit.getRepository().resolve("other")).call();
            assertThat(result.getMergeStatus()).isEqualTo(MergeResult.MergeStatus.CONFLICTING);
        }

        assertThat(git.status(repo).changes()).containsExactly(
                new FileChange("a.txt", null, ChangeType.CONFLICTED));
    }

    @Test
    void notARepositoryIsReportedAsGitException() {
        Path plain = repo.resolveSibling(repo.getFileName() + "-plain");
        assertThatThrownBy(() -> git.status(plain)).isInstanceOf(GitException.class);
    }

    @Test
    void fileNameAndDirectoryAreSplitOnSlash() {
        FileChange nested = new FileChange("src/main/App.java", null, ChangeType.MODIFIED);
        FileChange root = new FileChange("pom.xml", null, ChangeType.MODIFIED);

        assertThat(nested.fileName()).isEqualTo("App.java");
        assertThat(nested.directory()).isEqualTo("src/main");
        assertThat(root.fileName()).isEqualTo("pom.xml");
        assertThat(root.directory()).isEmpty();
    }

    private void write(String relative, String content) throws Exception {
        Path file = repo.resolve(relative);
        Files.createDirectories(file.getParent());
        Files.writeString(file, content, StandardCharsets.UTF_8);
    }

    private static void commit(Git jgit, String message) throws Exception {
        jgit.commit().setMessage(message).setAuthor(IDENT).setCommitter(IDENT).setSign(false).call();
    }
}
