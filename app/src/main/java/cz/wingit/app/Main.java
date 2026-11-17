package cz.wingit.app;

import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.Optional;

import javax.swing.SwingUtilities;

import cz.wingit.core.DefaultGitService;
import cz.wingit.core.GitService;
import cz.wingit.core.cli.GitCli;
import cz.wingit.core.repo.RepositoryList;
import cz.wingit.ui.Theme;
import cz.wingit.ui.main.MainWindow;

/**
 * Application entry point and wiring.
 */
public final class Main {

    private Main() {
    }

    public static void main(String[] args) {
        GitService git = new DefaultGitService(new GitCli());
        RepositoryList repositories = RepositoryList.load(AppPaths.repositoryListFile());
        AppSettings settings = AppSettings.load(AppPaths.settingsFile());

        SwingUtilities.invokeLater(() -> {
            Theme.install(Theme.Mode.LIGHT);
            MainWindow window = new MainWindow(git, repositories);
            window.setVersion(version());
            settings.windowLayout().ifPresent(window::applyLayout);
            settings.lastOpenedRepository().ifPresent(repo -> window.reopen(repo, settings.lastSelectedFile()));
            window.addOpenListener(opened -> rememberOpened(settings, opened));
            // Window listeners run before EXIT_ON_CLOSE ends the JVM.
            window.addWindowListener(new WindowAdapter() {
                @Override
                public void windowClosing(WindowEvent e) {
                    rememberLayout(settings, window);
                }
            });
            window.setVisible(true);
        });
    }

    /**
     * The jpackage launcher passes the release version ({@code --app-version}, taken from the tag) as
     * {@code jpackage.app-version}; a plain jar has the Maven version in its manifest; running from classes has none.
     */
    private static String version() {
        String version = System.getProperty("jpackage.app-version");
        if (version == null || version.isBlank()) {
            version = Main.class.getPackage().getImplementationVersion();
        }
        return version;
    }

    /** Window geometry and the selected file, saved when the window closes. */
    private static void rememberLayout(AppSettings settings, MainWindow window) {
        try {
            settings.setWindowLayout(window.currentLayout());
            settings.setLastSelectedFile(window.selectedFile());
        } catch (UncheckedIOException e) {
            // Not critical: the next start uses the default window size.
            System.err.println("WinGit: " + e.getMessage());
        }
    }

    private static void rememberOpened(AppSettings settings, Optional<Path> opened) {
        try {
            settings.setLastOpenedRepository(opened);
        } catch (UncheckedIOException e) {
            // Not critical: the app works without it, only the next start won't reopen the repository.
            System.err.println("WinGit: " + e.getMessage());
        }
    }
}
