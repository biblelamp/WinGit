package cz.wingit.ui.diff;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import javax.swing.JLabel;
import javax.swing.SwingUtilities;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.StoredConfig;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import cz.wingit.core.DefaultGitService;
import cz.wingit.core.cli.GitCli;
import cz.wingit.core.status.ChangeType;
import cz.wingit.core.status.FileChange;
import cz.wingit.ui.Theme;

class DiffPaneTest {

    private static final PersonIdent IDENT = new PersonIdent("Test", "test@example.invalid");

    @TempDir
    Path repo;

    @BeforeAll
    static void installTheme() {
        Theme.install(Theme.Mode.LIGHT);
    }

    @Test
    void showsAlignedSidesWithRealLineNumbers() throws Exception {
        try (Git git = Git.init().setDirectory(repo.toFile()).setInitialBranch("main").call()) {
            StoredConfig config = git.getRepository().getConfig();
            config.setString("core", null, "autocrlf", "false");
            config.save();
            write("App.java", "class App {\n  int a;\n}\n");
            git.add().addFilepattern("App.java").call();
            git.commit().setMessage("init").setAuthor(IDENT).setCommitter(IDENT).setSign(false).call();
        }
        write("App.java", "class App {\n  int a;\n  int b;\n}\n");

        AtomicReference<DiffPane> pane = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> {
            pane.set(new DiffPane(new DefaultGitService(new GitCli())));
            pane.get().showRepository(Optional.of(repo));
            pane.get().showFile(Optional.of(new FileChange("App.java", null, ChangeType.MODIFIED)));
        });
        awaitShown(pane.get(), "App.java");

        AtomicReference<String> leftText = new AtomicReference<>();
        AtomicReference<String> rightText = new AtomicReference<>();
        AtomicReference<String> leftGutterOfFiller = new AtomicReference<>();
        AtomicReference<String> leftGutterAfterFiller = new AtomicReference<>();
        AtomicReference<String> captions = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> {
            DiffView view = pane.get().view();
            leftText.set(view.left().textArea().getText());
            rightText.set(view.right().textArea().getText());
            leftGutterOfFiller.set(view.left().gutterText(3));
            leftGutterAfterFiller.set(view.left().gutterText(4));
            captions.set(view.left().getComponent(0) instanceof JLabel l ? l.getText() : "?");
        });

        // the added line 3 has an empty filler on the left, so "}" stays on the same row
        assertThat(leftText.get()).isEqualTo("class App {\n  int a;\n\n}");
        assertThat(rightText.get()).isEqualTo("class App {\n  int a;\n  int b;\n}");
        assertThat(leftGutterOfFiller.get()).isEmpty();
        assertThat(leftGutterAfterFiller.get()).isEqualTo("3");
        assertThat(captions.get()).isEqualTo("Index");
    }

    @Test
    void untrackedFileHasNoLeftSideAndUnknownTypesArePlainText() throws Exception {
        try (Git git = Git.init().setDirectory(repo.toFile()).setInitialBranch("main").call()) {
            // empty repository
        }
        write("notes.unknownext", "hello\n");

        AtomicReference<DiffPane> pane = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> {
            pane.set(new DiffPane(new DefaultGitService(new GitCli())));
            pane.get().showRepository(Optional.of(repo));
            pane.get().showFile(Optional.of(new FileChange("notes.unknownext", null, ChangeType.UNTRACKED)));
        });
        awaitShown(pane.get(), "notes.unknownext");

        AtomicReference<String> caption = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> caption.set(
                ((JLabel) pane.get().view().left().getComponent(0)).getText()));
        assertThat(caption.get()).isEqualTo("Index (file not present)");
        assertThat(DiffView.syntaxStyle("notes.unknownext")).isEqualTo("text/plain");
        assertThat(DiffView.syntaxStyle("src/Main.java")).isEqualTo("text/java");
    }

    private static void awaitShown(DiffPane pane, String path) throws Exception {
        AtomicReference<String> shown = new AtomicReference<>();
        long deadline = System.currentTimeMillis() + 10_000;
        do {
            SwingUtilities.invokeAndWait(() -> shown.set(pane.view().shownPath()));
            if (path.equals(shown.get())) {
                return;
            }
            Thread.sleep(50);
        } while (System.currentTimeMillis() < deadline);
        assertThat(shown.get()).as("diff shown").isEqualTo(path);
    }

    private void write(String relative, String content) throws Exception {
        Files.writeString(repo.resolve(relative), content, StandardCharsets.UTF_8);
    }
}
