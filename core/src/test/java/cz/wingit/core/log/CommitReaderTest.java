package cz.wingit.core.log;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.TimeZone;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.revwalk.RevCommit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import cz.wingit.core.DefaultGitService;
import cz.wingit.core.GitException;
import cz.wingit.core.GitService;
import cz.wingit.core.cli.GitCli;
import cz.wingit.core.diff.DiffRow;
import cz.wingit.core.diff.FileDiff;
import cz.wingit.core.diff.FileVersion;
import cz.wingit.core.status.ChangeType;

class CommitReaderTest {

    private static final String SHARED = "line 1\nline 2\nline 3\nline 4\nline 5\nline 6\nline 7\nline 8\n";

    @TempDir
    Path repo;

    private final GitService git = new DefaultGitService(new GitCli());

    @Test
    void readsMetadataMessageAndChangedFiles() throws Exception {
        PersonIdent author = ident("Jana Nováková", "jana@example.invalid", "2026-03-01T10:00:00Z");
        PersonIdent committer = ident("Petr", "petr@example.invalid", "2026-03-02T11:00:00Z");
        RevCommit first;
        RevCommit second;
        try (Git jgit = Git.init().setDirectory(repo.toFile()).setInitialBranch("main").call()) {
            write("keep.txt", "keep\n");
            write("change.txt", "old\n");
            write("remove.txt", "gone\n");
            write("old/name.txt", SHARED);
            jgit.add().addFilepattern(".").call();
            first = jgit.commit().setMessage("First").setAuthor(author).setCommitter(author).setSign(false).call();

            write("change.txt", "new\n");
            write("add.txt", "added\n");
            Files.delete(repo.resolve("remove.txt"));
            Files.delete(repo.resolve("old/name.txt"));
            write("new/name.txt", SHARED);
            jgit.add().addFilepattern(".").call();
            jgit.add().setUpdate(true).addFilepattern(".").call();
            second = jgit.commit().setMessage("Second\n\nWith a body.\n").setAuthor(author).setCommitter(committer)
                    .setSign(false).call();
        }

        CommitDetails details = git.commitDetails(repo, second.getName());

        assertThat(details.id()).isEqualTo(second.getName());
        assertThat(details.parents()).containsExactly(first.getName());
        assertThat(details.author()).isEqualTo(new CommitDetails.Person("Jana Nováková", "jana@example.invalid"));
        assertThat(details.authorTime()).isEqualTo(Instant.parse("2026-03-01T10:00:00Z"));
        assertThat(details.committer().name()).isEqualTo("Petr");
        assertThat(details.commitTime()).isEqualTo(Instant.parse("2026-03-02T11:00:00Z"));
        assertThat(details.message()).isEqualTo("Second\n\nWith a body.\n");
        assertThat(details.files()).containsExactly(
                new CommitFile("add.txt", ChangeType.ADDED),
                new CommitFile("change.txt", ChangeType.MODIFIED),
                new CommitFile("new/name.txt", ChangeType.RENAMED, "old/name.txt"),
                new CommitFile("remove.txt", ChangeType.DELETED));
    }

    @Test
    void rootCommitAddsAllItsFiles() throws Exception {
        RevCommit root;
        try (Git jgit = Git.init().setDirectory(repo.toFile()).setInitialBranch("main").call()) {
            write("a.txt", "a\n");
            write("dir/b.txt", "b\n");
            jgit.add().addFilepattern(".").call();
            root = commit(jgit, "Root");
        }

        CommitDetails details = git.commitDetails(repo, root.getName());

        assertThat(details.parents()).isEmpty();
        assertThat(details.files()).containsExactly(
                new CommitFile("a.txt", ChangeType.ADDED), new CommitFile("dir/b.txt", ChangeType.ADDED));

        FileDiff diff = git.commitFileDiff(repo, root.getName(), details.files().get(0));
        assertThat(diff.left().exists()).isFalse();
        assertThat(diff.left().source()).isEqualTo(FileVersion.Source.PARENT);
        assertThat(diff.right().text()).isEqualTo("a\n");
        assertThat(diff.right().revision()).isEqualTo(root.getName());
    }

    @Test
    void fileDiffComparesFirstParentWithCommit() throws Exception {
        RevCommit first;
        RevCommit second;
        try (Git jgit = Git.init().setDirectory(repo.toFile()).setInitialBranch("main").call()) {
            write("a.txt", "one\ntwo\n");
            jgit.add().addFilepattern(".").call();
            first = commit(jgit, "First");
            write("a.txt", "one\nTWO\n");
            jgit.add().addFilepattern(".").call();
            second = commit(jgit, "Second");
            write("a.txt", "uncommitted\n"); // the working tree does not matter
        }

        FileDiff diff = git.commitFileDiff(repo, second.getName(), new CommitFile("a.txt", ChangeType.MODIFIED));

        assertThat(diff.left().text()).isEqualTo("one\ntwo\n");
        assertThat(diff.left().revision()).isEqualTo(first.getName());
        assertThat(diff.right().text()).isEqualTo("one\nTWO\n");
        assertThat(diff.right().source()).isEqualTo(FileVersion.Source.COMMIT);
        assertThat(diff.rows()).extracting(DiffRow::type).contains(DiffRow.Type.SAME).hasSize(2);
        assertThat(diff.hasChanges()).isTrue();
    }

    @Test
    void renamedFileIsComparedWithItsOriginalPath() throws Exception {
        RevCommit second;
        try (Git jgit = Git.init().setDirectory(repo.toFile()).setInitialBranch("main").call()) {
            write("old.txt", SHARED);
            jgit.add().addFilepattern(".").call();
            commit(jgit, "First");
            Files.delete(repo.resolve("old.txt"));
            write("new.txt", SHARED.replace("line 8", "line eight"));
            jgit.add().addFilepattern(".").call();
            jgit.add().setUpdate(true).addFilepattern(".").call();
            second = commit(jgit, "Rename");
        }

        FileDiff diff = git.commitFileDiff(repo, second.getName(),
                new CommitFile("new.txt", ChangeType.RENAMED, "old.txt"));

        assertThat(diff.left().text()).isEqualTo(SHARED);
        assertThat(diff.right().text()).contains("line eight");
    }

    @Test
    void unknownCommitIsAnError() throws Exception {
        Git.init().setDirectory(repo.toFile()).setInitialBranch("main").call().close();

        assertThatThrownBy(() -> git.commitDetails(repo, "0123456789012345678901234567890123456789"))
                .isInstanceOf(GitException.class);
    }

    private static PersonIdent ident(String name, String email, String when) {
        return new PersonIdent(new PersonIdent(name, email), Date.from(Instant.parse(when)),
                TimeZone.getTimeZone(ZoneOffset.UTC));
    }

    private static RevCommit commit(Git jgit, String message) throws Exception {
        PersonIdent ident = new PersonIdent("Test", "test@example.invalid");
        return jgit.commit().setMessage(message).setAuthor(ident).setCommitter(ident).setSign(false).call();
    }

    private void write(String relative, String content) throws Exception {
        Path file = repo.resolve(relative);
        Files.createDirectories(file.getParent());
        Files.writeString(file, content, StandardCharsets.UTF_8);
    }
}
