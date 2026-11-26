package cz.wingit.core.merge;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Repository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import cz.wingit.core.DefaultGitService;
import cz.wingit.core.GitService;
import cz.wingit.core.cli.GitCli;

class MergeConflictsTest {

    @TempDir
    Path repo;

    private final GitService git = new DefaultGitService(new GitCli());
    private final PersonIdent ident = new PersonIdent("Test", "test@example.invalid");

    /** base: a.txt, b.txt, same.txt; main and feature both change a.txt (differently) and same.txt (equally). */
    private void divergedRepository() throws Exception {
        try (Git jgit = Git.init().setDirectory(repo.toFile()).setInitialBranch("main").call()) {
            write("a.txt", "line\n");
            write("b.txt", "b\n");
            write("same.txt", "old\n");
            commit(jgit, "base");
            jgit.branchCreate().setName("feature").call();
            jgit.branchCreate().setName("clean").call();
            write("a.txt", "main\n");
            write("same.txt", "new\n");
            commit(jgit, "main change");

            jgit.checkout().setName("feature").call();
            write("a.txt", "feature\n");
            write("same.txt", "new\n");
            write("c.txt", "c\n");
            commit(jgit, "feature change");

            jgit.checkout().setName("clean").call();
            write("b.txt", "clean\n");
            commit(jgit, "clean change");
            jgit.checkout().setName("main").call();
        }
    }

    @Test
    void trialMergeFindsExactlyTheConflictingFiles() throws Exception {
        divergedRepository();

        MergeConflicts conflicts = git.mergeConflicts(repo, "feature");

        assertThat(conflicts.certainty()).isEqualTo(MergeConflicts.Certainty.EXACT);
        assertThat(conflicts.files()).containsExactly("a.txt");
        assertThat(Files.readString(repo.resolve("a.txt"), StandardCharsets.UTF_8).strip()).isEqualTo("main");
        assertThat(git.mergeInProgress(repo)).isFalse();
    }

    @Test
    void cleanMergeHasNoConflicts() throws Exception {
        divergedRepository();

        MergeConflicts conflicts = git.mergeConflicts(repo, "clean");

        assertThat(conflicts.isEmpty()).isTrue();
    }

    @Test
    void fallbackListsFilesChangedDifferentlyOnBothSides() throws Exception {
        divergedRepository();

        try (Git jgit = Git.open(repo.toFile())) {
            Repository repository = jgit.getRepository();
            assertThat(MergePreviewReader.changedOnBothSides(repository, "feature")).containsExactly("a.txt");
            assertThat(MergePreviewReader.changedOnBothSides(repository, "clean")).isEmpty();
        }
    }

    private void write(String file, String content) throws Exception {
        Files.writeString(repo.resolve(file), content, StandardCharsets.UTF_8);
    }

    private void commit(Git jgit, String message) throws Exception {
        jgit.add().addFilepattern(".").call();
        jgit.commit().setMessage(message).setAuthor(ident).setCommitter(ident).setSign(false).call();
    }
}
