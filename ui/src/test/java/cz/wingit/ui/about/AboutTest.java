package cz.wingit.ui.about;

import static org.assertj.core.api.Assertions.assertThat;

import java.awt.GraphicsEnvironment;
import java.util.concurrent.atomic.AtomicReference;

import javax.swing.SwingUtilities;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import cz.wingit.core.DefaultGitService;
import cz.wingit.core.cli.GitCli;
import cz.wingit.ui.Theme;

class AboutTest {

    @BeforeAll
    static void installTheme() {
        Theme.install(Theme.Mode.LIGHT);
    }

    @Test
    void noticesPointToTheReleaseTagOrToMain() {
        assertThat(Links.thirdPartyNotices("0.3.3"))
                .isEqualTo("https://github.com/biblelamp/WinGit/blob/v0.3.3/THIRD-PARTY-NOTICES.md");
        assertThat(Links.thirdPartyNotices("0.3.3-SNAPSHOT")).contains("/blob/main/");
        assertThat(Links.thirdPartyNotices(null)).contains("/blob/main/");
    }

    @Test
    void addressesAreShownWithoutScheme() {
        assertThat(Links.display(Links.WEBSITE)).isEqualTo("wgit.cz");
        assertThat(Links.display(Links.SOURCE)).isEqualTo("github.com/biblelamp/WinGit");
    }

    @Test
    void detailsForBugReportsListAllVersions() {
        AboutInfo info = new AboutInfo("0.3.3", "17.0.16 (Oracle Corporation)", "Windows 11 10.0 (amd64)", null);

        assertThat(info.details()).isEqualTo("""
                WinGit 0.3.3
                Java: 17.0.16 (Oracle Corporation)
                Git: unknown
                OS: Windows 11 10.0 (amd64)
                """);
        assertThat(info.withGit("2.47.1.windows.1").details()).contains("Git: 2.47.1.windows.1");
        assertThat(new AboutInfo(null, "j", "o", "g").details()).startsWith("WinGit development build\n");
    }

    @Test
    void currentInfoDescribesThisMachine() {
        AboutInfo info = AboutInfo.current("1.0");

        assertThat(info.java()).startsWith(System.getProperty("java.version"));
        assertThat(info.os()).startsWith(System.getProperty("os.name"));
        assertThat(info.git()).isNull();
    }

    @Test
    void dialogReadsTheGitVersionInTheBackground() throws Exception {
        Assumptions.assumeFalse(GraphicsEnvironment.isHeadless(), "needs a display");
        AtomicReference<AboutDialog> dialog = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() ->
                dialog.set(new AboutDialog(null, new DefaultGitService(new GitCli()), "0.3.3")));
        try {
            long deadline = System.currentTimeMillis() + 10_000;
            AtomicReference<String> details = new AtomicReference<>("");
            while (System.currentTimeMillis() < deadline && details.get().contains("Git: unknown")
                    || details.get().isEmpty()) {
                SwingUtilities.invokeAndWait(() -> details.set(dialog.get().details()));
                Thread.sleep(50);
            }
            assertThat(details.get()).startsWith("WinGit 0.3.3\n").containsPattern("Git: \\d+\\.\\d+");
        } finally {
            SwingUtilities.invokeAndWait(() -> dialog.get().dispose());
        }
    }
}
