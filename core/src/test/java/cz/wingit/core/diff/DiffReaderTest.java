package cz.wingit.core.diff;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.StoredConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import cz.wingit.core.DefaultGitService;
import cz.wingit.core.GitService;
import cz.wingit.core.cli.GitCli;
import cz.wingit.core.diff.DiffRow.Type;
import cz.wingit.core.diff.FileVersion.Source;
import cz.wingit.core.status.ChangeType;
import cz.wingit.core.status.FileChange;

class DiffReaderTest {

    private static final PersonIdent IDENT = new PersonIdent("Test", "test@example.invalid");

    @TempDir
    Path repo;

    private final GitService git = new DefaultGitService(new GitCli());

    @BeforeEach
    void createRepository() throws Exception {
        try (Git jgit = Git.init().setDirectory(repo.toFile()).setInitialBranch("main").call()) {
            StoredConfig config = jgit.getRepository().getConfig();
            config.setString("core", null, "autocrlf", "false"); // independent of the developer's global config
            config.save();
            write("a.txt", "one\ntwo\n");
            jgit.add().addFilepattern("a.txt").call();
            jgit.commit().setMessage("init").setAuthor(IDENT).setCommitter(IDENT).setSign(false).call();
        }
    }

    @Test
    void unstagedChangeComparesIndexWithWorkingTree() throws Exception {
        write("a.txt", "one\nTWO\nthree\n");

        FileDiff diff = git.fileDiff(repo, new FileChange("a.txt", null, ChangeType.MODIFIED));

        assertThat(diff.left().source()).isEqualTo(Source.INDEX);
        assertThat(diff.right().source()).isEqualTo(Source.WORKING_TREE);
        assertThat(diff.rows()).extracting(DiffRow::type).containsExactly(Type.SAME, Type.CHANGED, Type.ADDED);
    }

    @Test
    void stagedOnlyChangeComparesHeadWithIndex() throws Exception {
        write("a.txt", "one\nstaged\n");
        try (Git jgit = Git.open(repo.toFile())) {
            jgit.add().addFilepattern("a.txt").call();
        }

        FileDiff diff = git.fileDiff(repo, new FileChange("a.txt", ChangeType.MODIFIED, null));

        assertThat(diff.left().source()).isEqualTo(Source.HEAD);
        assertThat(diff.right().source()).isEqualTo(Source.INDEX);
        assertThat(diff.right().text()).isEqualTo("one\nstaged\n");
    }

    @Test
    void untrackedAndDeletedFilesHaveOneMissingSide() throws Exception {
        write("new.txt", "fresh\n");
        FileDiff added = git.fileDiff(repo, new FileChange("new.txt", null, ChangeType.UNTRACKED));
        assertThat(added.left().exists()).isFalse();
        assertThat(added.rows()).containsExactly(new DiffRow(Type.ADDED, 0, null, 1, "fresh"));

        Files.delete(repo.resolve("a.txt"));
        FileDiff deleted = git.fileDiff(repo, new FileChange("a.txt", null, ChangeType.DELETED));
        assertThat(deleted.right().exists()).isFalse();
        assertThat(deleted.rows()).extracting(DiffRow::type).containsOnly(Type.DELETED);
    }

    @Test
    void autocrlfWorkingCopyWithCrlfShowsNoFalseChanges() throws Exception {
        try (Git jgit = Git.open(repo.toFile())) {
            StoredConfig config = jgit.getRepository().getConfig();
            config.setString("core", null, "autocrlf", "true");
            config.save();
        }
        write("a.txt", "one\r\ntwo\r\n"); // how a checkout with autocrlf=true looks on Windows

        FileDiff diff = git.fileDiff(repo, new FileChange("a.txt", null, ChangeType.MODIFIED));

        assertThat(diff.hasChanges()).isFalse();
    }

    @Test
    void binaryFileIsNotCompared() throws Exception {
        Files.write(repo.resolve("image.bin"), new byte[] {1, 0, 2, 0, 3});

        FileDiff diff = git.fileDiff(repo, new FileChange("image.bin", null, ChangeType.UNTRACKED));

        assertThat(diff.isBinary()).isTrue();
        assertThat(diff.rows()).isEmpty();
    }

    private void write(String relative, String content) throws Exception {
        Files.writeString(repo.resolve(relative), content, StandardCharsets.UTF_8);
    }
}
