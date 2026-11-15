package cz.wingit.core.refs;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.PersonIdent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import cz.wingit.core.DefaultGitService;
import cz.wingit.core.GitBranchNotMergedException;
import cz.wingit.core.GitCommandException;
import cz.wingit.core.GitService;
import cz.wingit.core.cli.GitCli;

class BranchCreateTest {

    @TempDir
    Path repo;

    private final GitService git = new DefaultGitService(new GitCli());

    @Test
    void validatesBranchNames() {
        assertThat(BranchNames.isValid("feature/login-2")).isTrue();
        assertThat(BranchNames.isValid("Žluťoučký")).isTrue();
        assertThat(BranchNames.isValid("")).isFalse();
        assertThat(BranchNames.isValid("with space")).isFalse();
        assertThat(BranchNames.isValid("a..b")).isFalse();
        assertThat(BranchNames.isValid("-x")).isFalse();
        assertThat(BranchNames.isValid("HEAD")).isFalse();
        assertThat(BranchNames.isValid("x.lock")).isFalse();
        assertThat(BranchNames.isValid("a:b")).isFalse();
        assertThat(BranchNames.isValid("end/")).isFalse();
    }

    @Test
    void existingNamesCompareIgnoringCase() {
        assertThat(BranchNames.exists("Feature", List.of("main", "feature"))).isTrue();
        assertThat(BranchNames.exists("feature2", List.of("main", "feature"))).isFalse();
    }

    @Test
    void createsBranchWithAndWithoutCheckout() throws Exception {
        commitFile();
        String original = git.currentBranch(repo).orElseThrow();

        git.createBranch(repo, "topic", false);
        assertThat(git.currentBranch(repo)).contains(original);

        Files.writeString(repo.resolve("a.txt"), "local change", StandardCharsets.UTF_8);
        git.createBranch(repo, "feature/x", true);
        assertThat(git.currentBranch(repo)).contains("feature/x");
        assertThat(repo.resolve("a.txt")).hasContent("local change"); // changes are kept
        assertThat(git.refs(repo).localBranches()).extracting(LocalBranch::name)
                .contains(original, "topic", "feature/x");

        assertThatThrownBy(() -> git.createBranch(repo, "topic", false)).isInstanceOf(GitCommandException.class);
        assertThatThrownBy(() -> git.createBranch(repo, "bad name", false))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void deletesMergedBranchAndAsksForForceOnUnmergedOne() throws Exception {
        commitFile();
        String original = git.currentBranch(repo).orElseThrow();
        git.createBranch(repo, "merged", false);
        git.createBranch(repo, "work", true);
        Files.writeString(repo.resolve("b.txt"), "new", StandardCharsets.UTF_8);
        try (Git jgit = Git.open(repo.toFile())) {
            jgit.add().addFilepattern("b.txt").call();
            PersonIdent ident = new PersonIdent("Test", "test@example.invalid");
            jgit.commit().setMessage("work").setAuthor(ident).setCommitter(ident).setSign(false).call();
        }
        git.switchBranch(repo, original);

        git.deleteBranch(repo, "merged", false);
        assertThatThrownBy(() -> git.deleteBranch(repo, "work", false))
                .isInstanceOfSatisfying(GitBranchNotMergedException.class,
                        e -> assertThat(e.branch()).isEqualTo("work"));
        assertThat(names()).containsExactlyInAnyOrder(original, "work");

        git.deleteBranch(repo, "work", true);
        assertThat(names()).containsExactly(original);
        assertThatThrownBy(() -> git.deleteBranch(repo, original, true)).isInstanceOf(GitCommandException.class);
    }

    @Test
    void renamesBranchesIncludingCurrentAndCaseOnly() throws Exception {
        commitFile();
        git.createBranch(repo, "topic", false);
        git.createBranch(repo, "work", true);

        git.renameBranch(repo, "topic", "topic-2");
        git.renameBranch(repo, "work", "feature/work"); // the checked-out one
        assertThat(git.currentBranch(repo)).contains("feature/work");
        git.renameBranch(repo, "topic-2", "Topic-2");

        assertThat(names()).contains("Topic-2", "feature/work").doesNotContain("topic", "work", "topic-2");
        assertThatThrownBy(() -> git.renameBranch(repo, "Topic-2", "feature/work"))
                .isInstanceOf(GitCommandException.class);
        assertThatThrownBy(() -> git.renameBranch(repo, "Topic-2", "bad name"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private List<String> names() {
        return git.refs(repo).localBranches().stream().map(LocalBranch::name).toList();
    }

    private void commitFile() throws Exception {
        try (Git jgit = Git.init().setDirectory(repo.toFile()).call()) {
            Files.writeString(repo.resolve("a.txt"), "hello", StandardCharsets.UTF_8);
            jgit.add().addFilepattern("a.txt").call();
            PersonIdent ident = new PersonIdent("Test", "test@example.invalid");
            jgit.commit().setMessage("initial").setAuthor(ident).setCommitter(ident).setSign(false).call();
        }
    }
}
