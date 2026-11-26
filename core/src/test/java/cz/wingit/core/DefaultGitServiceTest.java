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
    void parsesMergeTreeConflictsOncePerPath() {
        String out = "7af980caafe2732666c31fa09df6c4ebd75c16c9\0a.txt\0dir/ž b.txt\0a.txt\0";

        assertThat(DefaultGitService.parseMergeTreeConflicts(out)).containsExactly("a.txt", "dir/ž b.txt");
        assertThat(DefaultGitService.parseMergeTreeConflicts("7af980caafe2732666c31fa09df6c4ebd75c16c9\0")).isEmpty();
    }

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

    @Test
    void switchBranchChecksOutLocalBranch() throws Exception {
        Path repo = createRepoWithCommit("switching");
        try (Git jgit = Git.open(repo.toFile())) {
            jgit.branchCreate().setName("feature").call();
        }

        git.switchBranch(repo, "feature");

        assertThat(git.currentBranch(repo)).contains("feature");
    }

    @Test
    void switchBranchRefusesToOverwriteLocalChanges() throws Exception {
        Path repo = createRepoWithCommit("guarded");
        String original = git.currentBranch(repo).orElseThrow();
        try (Git jgit = Git.open(repo.toFile())) {
            jgit.checkout().setCreateBranch(true).setName("other").call();
            Files.writeString(repo.resolve("readme.txt"), "other", StandardCharsets.UTF_8);
            jgit.add().addFilepattern("readme.txt").call();
            PersonIdent ident = new PersonIdent("Test", "test@example.invalid");
            jgit.commit().setMessage("other").setAuthor(ident).setCommitter(ident).setSign(false).call();
            jgit.checkout().setName(original).call();
        }
        Files.writeString(repo.resolve("readme.txt"), "local edit", StandardCharsets.UTF_8);

        assertThatThrownBy(() -> git.switchBranch(repo, "other")).isInstanceOf(GitCommandException.class);
        assertThat(git.currentBranch(repo)).contains(original);
        assertThat(Files.readString(repo.resolve("readme.txt"), StandardCharsets.UTF_8)).isEqualTo("local edit");
    }

    @Test
    void switchToNewTrackingBranchCreatesBranchFromRemote() throws Exception {
        Path source = createRepoWithCommit("upstream");
        try (Git jgit = Git.open(source.toFile())) {
            jgit.branchCreate().setName("feature").call();
        }
        Path clone = tempDir.resolve("downstream");
        git.clone(source.toUri().toString(), clone, line -> { });

        git.switchToNewTrackingBranch(clone, "origin", "feature");

        assertThat(git.currentBranch(clone)).contains("feature");
        assertThat(git.refs(clone).localBranches())
                .anySatisfy(b -> {
                    assertThat(b.name()).isEqualTo("feature");
                    assertThat(b.upstream()).isEqualTo("origin/feature");
                });
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
