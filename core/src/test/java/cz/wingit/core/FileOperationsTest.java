package cz.wingit.core;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.StoredConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import cz.wingit.core.cli.GitCli;
import cz.wingit.core.status.ChangeType;
import cz.wingit.core.status.FileChange;

class FileOperationsTest {

    @TempDir
    Path repo;

    private final GitService git = new DefaultGitService(new GitCli());

    @Test
    void stageAndUnstageIncludingNewAndDeletedFiles() throws Exception {
        init();
        write("kept.txt", "1");
        write("gone.txt", "2");
        git.commit(repo, git.status(repo).changes(), "initial");
        write("kept.txt", "changed");
        Files.delete(repo.resolve("gone.txt"));
        write("new [1].txt", "new");

        git.stage(repo, git.status(repo).changes());
        assertThat(git.status(repo).changes()).containsExactly(
                new FileChange("gone.txt", ChangeType.DELETED, null),
                new FileChange("kept.txt", ChangeType.MODIFIED, null),
                new FileChange("new [1].txt", ChangeType.ADDED, null));

        git.unstage(repo, git.status(repo).changes());
        assertThat(git.status(repo).changes()).containsExactly(
                new FileChange("gone.txt", null, ChangeType.DELETED),
                new FileChange("kept.txt", null, ChangeType.MODIFIED),
                new FileChange("new [1].txt", null, ChangeType.UNTRACKED));
        assertThat(repo.resolve("kept.txt")).hasContent("changed"); // working tree untouched
    }

    @Test
    void unstageWorksBeforeTheFirstCommit() throws Exception {
        init();
        write("a.txt", "a");
        git.stage(repo, git.status(repo).changes());
        assertThat(git.status(repo).changes()).containsExactly(new FileChange("a.txt", ChangeType.ADDED, null));

        git.unstage(repo, git.status(repo).changes());

        assertThat(git.status(repo).changes()).containsExactly(new FileChange("a.txt", null, ChangeType.UNTRACKED));
    }

    @Test
    void discardRestoresTrackedAndDeletesNewFiles() throws Exception {
        init();
        write("mod.txt", "original");
        write("del.txt", "original");
        write("staged.txt", "original");
        git.commit(repo, git.status(repo).changes(), "initial");
        write("mod.txt", "changed");
        Files.delete(repo.resolve("del.txt"));
        write("staged.txt", "staged change");
        write("untracked.txt", "u");
        write("added.txt", "a");
        git.stage(repo, git.status(repo).changes().stream()
                .filter(c -> c.path().equals("staged.txt") || c.path().equals("added.txt")).toList());
        write("staged.txt", "staged and more");
        write("keep-me.txt", "not selected");
        List<FileChange> selected = git.status(repo).changes().stream()
                .filter(c -> !c.path().equals("keep-me.txt")).toList();

        git.discard(repo, selected);

        assertThat(git.status(repo).changes())
                .containsExactly(new FileChange("keep-me.txt", null, ChangeType.UNTRACKED));
        assertThat(repo.resolve("mod.txt")).hasContent("original");
        assertThat(repo.resolve("del.txt")).hasContent("original");
        assertThat(repo.resolve("staged.txt")).hasContent("original");
        assertThat(repo.resolve("untracked.txt")).doesNotExist();
        assertThat(repo.resolve("added.txt")).doesNotExist();
    }

    @Test
    void statusIsCleanRightAfterDiscardOfMultiLineFiles() throws Exception {
        init();
        for (int i = 0; i < 5; i++) {
            write("f" + i + ".txt", "line 1\nline 2\nline 3\n");
        }
        git.commit(repo, git.status(repo).changes(), "initial");
        for (int round = 0; round < 5; round++) {
            for (int i = 0; i < 5; i++) {
                write("f" + i + ".txt", "line 1\nchanged " + round + "\nline 3\n");
            }
            assertThat(git.status(repo).changes()).hasSize(5);

            git.discard(repo, git.status(repo).changes());

            assertThat(git.status(repo).changes()).as("round %d", round).isEmpty();
        }
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
    }

    private void write(String path, String text) throws Exception {
        Files.writeString(repo.resolve(path), text, StandardCharsets.UTF_8);
    }
}
