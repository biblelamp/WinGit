package cz.wingit.ui.log;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import javax.swing.JSplitPane;
import javax.swing.SwingUtilities;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.revwalk.RevCommit;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import cz.wingit.core.DefaultGitService;
import cz.wingit.core.cli.GitCli;
import cz.wingit.core.log.CommitFile;
import cz.wingit.core.status.ChangeType;
import cz.wingit.ui.Theme;
import cz.wingit.ui.branches.BranchesPane;
import cz.wingit.ui.diff.DiffPane;
import cz.wingit.ui.history.HistoryPane;

class LogContentTest {

    private static final PersonIdent IDENT = new PersonIdent("Jana Nováková", "jana@example.invalid");

    @TempDir
    Path repo;

    @BeforeAll
    static void installTheme() {
        Theme.install(Theme.Mode.LIGHT);
    }

    @Test
    void branchesAndHistoryOnTopCommitColumnRightDiffBelow() throws Exception {
        AtomicReference<LogContent> content = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> content.set(new LogContent(new DefaultGitService(new GitCli()))));

        JSplitPane main = content.get().splitPane("main");
        JSplitPane top = content.get().splitPane("top");
        JSplitPane center = content.get().splitPane("center");
        JSplitPane commit = content.get().splitPane("commit");
        assertThat(main.getOrientation()).isEqualTo(JSplitPane.VERTICAL_SPLIT);
        assertThat(main.getTopComponent()).isSameAs(top);
        assertThat(main.getBottomComponent()).isInstanceOf(DiffPane.class);
        assertThat(top.getLeftComponent()).isInstanceOf(BranchesPane.class);
        assertThat(top.getRightComponent()).isSameAs(center);
        assertThat(center.getLeftComponent()).isInstanceOf(HistoryPane.class);
        assertThat(center.getRightComponent()).isSameAs(commit);
        assertThat(commit.getTopComponent()).isInstanceOf(CommitDetailsPane.class);
        assertThat(commit.getBottomComponent()).isInstanceOf(CommitFilesPane.class);
    }

    @Test
    void selectedCommitShowsItsDetailsAndFiles() throws Exception {
        RevCommit first;
        try (Git git = Git.init().setDirectory(repo.toFile()).setInitialBranch("main").call()) {
            write("a.txt", "one\n");
            git.add().addFilepattern(".").call();
            first = git.commit().setMessage("First line\n\nBody text").setAuthor(IDENT).setCommitter(IDENT)
                    .setSign(false).call();
            write("b.txt", "two\n");
            git.add().addFilepattern(".").call();
            git.commit().setMessage("Second").setAuthor(IDENT).setCommitter(IDENT).setSign(false).call();
        }
        AtomicReference<LogContent> content = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> {
            content.set(new LogContent(new DefaultGitService(new GitCli())));
            content.get().showRepository(repo);
            content.get().selectCommit(first.getName());
        });

        List<CommitFile> files = await(() -> content.get().filesPane().files(), f -> !f.isEmpty());
        assertThat(files).containsExactly(new CommitFile("a.txt", ChangeType.ADDED));
        SwingUtilities.invokeAndWait(() -> {
            String details = content.get().detailsPane().shownText();
            assertThat(details).contains(first.getName(), "Jana Nováková <jana@example.invalid>",
                    "First line", "Body text");
            assertThat(content.get().filesPane().selectedFile()).contains(files.get(0));
        });
    }

    @Test
    void dividersAreSavedAndRestoredByName() throws Exception {
        AtomicReference<LogContent> content = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> {
            content.set(new LogContent(new DefaultGitService(new GitCli())));
            content.get().restoreDividers(java.util.Map.of(
                    "main", new cz.wingit.ui.common.WindowLayout.Divider(500, 900),
                    "commit", new cz.wingit.ui.common.WindowLayout.Divider(200, 450),
                    "unknown", new cz.wingit.ui.common.WindowLayout.Divider(10, 100)));
            content.get().setSize(1300, 900);
            content.get().doLayout();
            content.get().splitPane("main").doLayout();
        });
        SwingUtilities.invokeAndWait(() -> {
            java.util.Map<String, cz.wingit.ui.common.WindowLayout.Divider> dividers = content.get().dividers();
            assertThat(dividers).containsOnlyKeys("main", "top", "center", "commit");
            assertThat(dividers.get("main").location()).isEqualTo(500);
            assertThat(dividers.get("main").splitSize()).isEqualTo(900);
        });
    }

    @Test
    void titleNamesTheRepository() {
        assertThat(LogWindow.title(Path.of("work", "WinGit"))).startsWith("WinGit");
    }

    private static <T> T await(Supplier<T> read, java.util.function.Predicate<T> done) throws Exception {
        AtomicReference<T> value = new AtomicReference<>();
        long deadline = System.currentTimeMillis() + 10_000;
        do {
            SwingUtilities.invokeAndWait(() -> value.set(read.get()));
            if (done.test(value.get())) {
                return value.get();
            }
            Thread.sleep(50);
        } while (System.currentTimeMillis() < deadline);
        return value.get();
    }

    private void write(String relative, String text) throws Exception {
        Files.writeString(repo.resolve(relative), text, StandardCharsets.UTF_8);
    }
}
