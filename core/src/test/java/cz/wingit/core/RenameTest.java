package cz.wingit.core;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.StoredConfig;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import cz.wingit.core.cli.GitCli;
import cz.wingit.core.diff.FileDiff;
import cz.wingit.core.status.ChangeType;
import cz.wingit.core.status.FileChange;

/** A file moved and staged (like {@code git mv}) is one renamed entry, and every action handles both paths. */
class RenameTest {

    private static final String CONTENT = "line 1\nline 2\nline 3\nline 4\nline 5\nline 6\nline 7\nline 8\n";

    @TempDir
    Path repo;

    private final GitService git = new DefaultGitService(new GitCli());

    @BeforeEach
    void commitOriginal() throws Exception {
        git.init(repo);
        try (Git jgit = Git.open(repo.toFile())) {
            StoredConfig config = jgit.getRepository().getConfig();
            config.setString("user", null, "name", "Test");
            config.setString("user", null, "email", "test@example.invalid");
            config.setBoolean("commit", null, "gpgsign", false);
            config.setString("core", null, "hooksPath", "no-hooks");
            config.save();
        }
        write("old/Dialog.java", CONTENT);
        write("other.txt", "other");
        git.commit(repo, git.status(repo).changes(), "initial");
    }

    @Test
    void stagedMoveIsReportedAsOneRename() throws Exception {
        moveAndStage();

        assertThat(git.status(repo).changes()).containsExactly(
                FileChange.renamed("old/Dialog.java", "common/Dialog.java", null));

        write("common/Dialog.java", CONTENT + "line 9\n");
        assertThat(git.status(repo).changes()).containsExactly(
                FileChange.renamed("old/Dialog.java", "common/Dialog.java", ChangeType.MODIFIED));
    }

    @Test
    void unstagedMoveStaysDeletionAndUntrackedFile() throws Exception {
        Files.createDirectories(repo.resolve("common"));
        Files.move(repo.resolve("old/Dialog.java"), repo.resolve("common/Dialog.java"));

        assertThat(git.status(repo).changes()).containsExactly(
                new FileChange("common/Dialog.java", null, ChangeType.UNTRACKED),
                new FileChange("old/Dialog.java", null, ChangeType.DELETED));
    }

    @Test
    void fileCreatedAgainAtTheOriginalPathStaysUntracked() throws Exception {
        moveAndStage();
        write("old/Dialog.java", "a new file");

        assertThat(git.status(repo).changes()).containsExactly(
                FileChange.renamed("old/Dialog.java", "common/Dialog.java", null),
                new FileChange("old/Dialog.java", null, ChangeType.UNTRACKED));
    }

    @Test
    void diffComparesTheOriginalWithTheMovedFile() throws Exception {
        moveAndStage();

        FileDiff diff = git.fileDiff(repo, rename());

        assertThat(diff.left().exists()).isTrue();
        assertThat(diff.left().text()).isEqualTo(CONTENT);
        assertThat(diff.right().text()).isEqualTo(CONTENT);
    }

    @Test
    void unstageUndoesBothSidesOfTheRename() throws Exception {
        moveAndStage();

        git.unstage(repo, List.of(rename()));

        assertThat(git.status(repo).changes()).containsExactly(
                new FileChange("common/Dialog.java", null, ChangeType.UNTRACKED),
                new FileChange("old/Dialog.java", null, ChangeType.DELETED));
    }

    @Test
    void commitIncludesTheDeletionOfTheOriginal() throws Exception {
        moveAndStage();
        write("other.txt", "not selected");

        git.commit(repo, List.of(rename()), "move dialog");

        assertThat(git.status(repo).changes()).containsExactly(
                new FileChange("other.txt", null, ChangeType.MODIFIED));
        assertThat(inHead("common/Dialog.java")).isTrue();
        assertThat(inHead("old/Dialog.java")).isFalse();
    }

    @Test
    void discardMovesTheFileBack() throws Exception {
        moveAndStage();
        write("common/Dialog.java", CONTENT + "changed after the move\n");

        git.discard(repo, git.status(repo).changes());

        assertThat(git.status(repo).isClean()).isTrue();
        assertThat(repo.resolve("old/Dialog.java")).hasContent(CONTENT.strip());
        assertThat(repo.resolve("common/Dialog.java")).doesNotExist();
    }

    private void moveAndStage() throws Exception {
        Files.createDirectories(repo.resolve("common"));
        Files.move(repo.resolve("old/Dialog.java"), repo.resolve("common/Dialog.java"));
        git.stage(repo, git.status(repo).changes());
    }

    private FileChange rename() {
        return git.status(repo).changes().stream()
                .filter(c -> c.staged() == ChangeType.RENAMED).findFirst().orElseThrow();
    }

    private boolean inHead(String path) throws Exception {
        try (Git jgit = Git.open(repo.toFile());
             TreeWalk walk = TreeWalk.forPath(jgit.getRepository(), path,
                     jgit.getRepository().resolve("HEAD^{tree}"))) {
            return walk != null;
        }
    }

    private void write(String path, String text) throws Exception {
        Path file = repo.resolve(path);
        Files.createDirectories(file.getParent());
        Files.writeString(file, text, StandardCharsets.UTF_8);
    }
}
