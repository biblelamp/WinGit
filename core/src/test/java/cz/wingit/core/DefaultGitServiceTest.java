package cz.wingit.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.PersonIdent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import cz.wingit.core.cli.GitCli;

class DefaultGitServiceTest {

    @TempDir
    Path tempDir;

    private final GitService git = new DefaultGitService(new GitCli());

    @Test
    void initCreatesRepository() throws Exception {
        Path dir = Files.createDirectory(tempDir.resolve("fresh"));

        git.init(dir);

        assertThat(git.isWorkTree(dir)).isTrue();
        assertThat(git.currentBranch(dir)).isPresent();
    }

    @Test
    void findsWorkTreeRootFromSubfolder() throws Exception {
        Path repo = createRepoWithCommit("root");
        Path nested = Files.createDirectories(repo.resolve("a/b"));

        assertThat(git.findWorkTreeRoot(nested)).contains(repo);
        assertThat(git.findWorkTreeRoot(tempDir)).isEmpty();
    }

    @Test
    void currentBranchOfDetachedHeadIsAbbreviatedId() throws Exception {
        Path repo = createRepoWithCommit("detached");
        try (Git jgit = Git.open(repo.toFile())) {
            String id = jgit.getRepository().resolve("HEAD").name();
            jgit.checkout().setName(id).call();

            assertThat(git.currentBranch(repo)).contains(id.substring(0, 7));
        }
    }

    @Test
    void cloneCopiesRepositoryAndReportsProgress() throws Exception {
        Path source = createRepoWithCommit("source");
        Path target = tempDir.resolve("clones/copy");
        List<String> progress = new ArrayList<>();

        git.clone(source.toString(), target, progress::add);

        assertThat(git.isWorkTree(target)).isTrue();
        assertThat(target.resolve("readme.txt")).hasContent("hello");
        assertThat(progress).isNotEmpty();
    }

    @Test
    void failedCloneReportsGitErrorAndRemovesTarget() {
        Path target = tempDir.resolve("broken");

        assertThatThrownBy(() -> git.clone(tempDir.resolve("does-not-exist").toString(), target, line -> { }))
                .isInstanceOfSatisfying(GitCommandException.class, e -> {
                    assertThat(e.exitCode()).isNotZero();
                    assertThat(e.stderr()).isNotBlank();
                });
        assertThat(target).doesNotExist();
    }

    @Test
    void interruptedCloneIsCancelledAndRemovesTarget() throws Exception {
        Path source = createRepoWithCommit("source2");
        Path target = tempDir.resolve("cancelled");

        Thread.currentThread().interrupt();
        try {
            assertThatThrownBy(() -> git.clone(source.toString(), target, line -> { }))
                    .isInstanceOf(GitCancelledException.class);
        } finally {
            Thread.interrupted();
        }
        assertThat(target).doesNotExist();
    }

    private Path createRepoWithCommit(String name) throws Exception {
        Path dir = Files.createDirectory(tempDir.resolve(name));
        try (Git jgit = Git.init().setDirectory(dir.toFile()).call()) {
            Files.writeString(dir.resolve("readme.txt"), "hello", StandardCharsets.UTF_8);
            jgit.add().addFilepattern("readme.txt").call();
            PersonIdent ident = new PersonIdent("Test", "test@example.invalid");
            jgit.commit().setMessage("initial").setAuthor(ident).setCommitter(ident).setSign(false).call();
        }
        return dir;
    }
}
