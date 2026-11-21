package cz.wingit.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.StoredConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import cz.wingit.core.cli.GitCli;
import cz.wingit.core.refs.Stash;

class StashTest {

    @TempDir
    Path repo;

    private final GitService git = new DefaultGitService(new GitCli());

    @Test
    void stashesChangesWithMessageAndPopsThemBack() throws Exception {
        init();
        write("a.txt", "edited\n");
        write("new.txt", "new file\n");

        assertThat(git.stash(repo, "  half-done\nlogin  ", true)).isTrue();

        assertThat(git.status(repo).isClean()).isTrue();
        assertThat(repo.resolve("a.txt")).hasContent("base\n");
        assertThat(repo.resolve("new.txt")).doesNotExist();
        List<Stash> stashes = git.refs(repo).stashes();
        assertThat(stashes).hasSize(1);
        assertThat(stashes.get(0).message()).endsWith(": half-done login");

        assertThat(git.applyStash(repo, stashes.get(0), true)).isEmpty();

        assertThat(repo.resolve("a.txt")).hasContent("edited\n");
        assertThat(repo.resolve("new.txt")).hasContent("new file\n");
        assertThat(git.refs(repo).stashes()).isEmpty();
    }

    @Test
    void untrackedFilesStayUnlessIncludedAndNothingToStashIsReported() throws Exception {
        init();
        assertThat(git.stash(repo, "", true)).isFalse();

        write("new.txt", "new file\n");
        assertThat(git.stash(repo, "", false)).isFalse();
        assertThat(repo.resolve("new.txt")).exists();

        write("a.txt", "edited\n");
        assertThat(git.stash(repo, null, false)).isTrue();
        assertThat(repo.resolve("new.txt")).exists();
        assertThat(git.refs(repo).stashes().get(0).message()).startsWith("WIP on ");
    }

    @Test
    void applyKeepsTheStashAndDropDeletesIt() throws Exception {
        init();
        write("a.txt", "first\n");
        git.stash(repo, "first", true);
        write("a.txt", "second\n");
        git.stash(repo, "second", true);
        List<Stash> stashes = git.refs(repo).stashes();
        assertThat(stashes).extracting(Stash::index).containsExactly(0, 1);

        assertThat(git.applyStash(repo, stashes.get(1), false)).isEmpty();
        assertThat(repo.resolve("a.txt")).hasContent("first\n");
        assertThat(git.refs(repo).stashes()).hasSize(2);

        git.dropStash(repo, stashes.get(0));
        List<Stash> left = git.refs(repo).stashes();
        assertThat(left).hasSize(1);
        assertThat(left.get(0).message()).endsWith(": first");
    }

    @Test
    void refusesAStashWhoseListChanged() throws Exception {
        init();
        write("a.txt", "first\n");
        git.stash(repo, "first", true);
        Stash old = git.refs(repo).stashes().get(0);
        write("a.txt", "second\n");
        git.stash(repo, "second", true); // "first" is stash@{1} now

        assertThatThrownBy(() -> git.dropStash(repo, old)).isInstanceOf(GitException.class)
                .hasMessageContaining("changed");
        assertThatThrownBy(() -> git.applyStash(repo, old, true)).isInstanceOf(GitException.class);
        assertThat(git.refs(repo).stashes()).hasSize(2);
    }

    @Test
    void conflictingPopReportsFilesAndKeepsTheStash() throws Exception {
        init();
        write("a.txt", "stashed version\n");
        git.stash(repo, "wip", true);
        commit("a.txt", "committed version\n", "other change");

        List<String> conflicts = git.applyStash(repo, git.refs(repo).stashes().get(0), true);

        assertThat(conflicts).containsExactly("a.txt");
        assertThat(git.refs(repo).stashes()).hasSize(1);
    }

    @Test
    void refusesToOverwriteLocalChanges() throws Exception {
        init();
        write("a.txt", "stashed version\n");
        git.stash(repo, "wip", true);
        write("a.txt", "local edit\n");

        assertThatThrownBy(() -> git.applyStash(repo, git.refs(repo).stashes().get(0), true))
                .isInstanceOf(GitCommandException.class);
        assertThat(repo.resolve("a.txt")).hasContent("local edit\n");
        assertThat(git.refs(repo).stashes()).hasSize(1);
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
    }

    private void write(String path, String content) throws Exception {
        Files.writeString(repo.resolve(path), content, StandardCharsets.UTF_8);
    }

    private void commit(String path, String content, String message) throws Exception {
        write(path, content);
        git.commit(repo, git.status(repo).changes(), message);
    }
}
