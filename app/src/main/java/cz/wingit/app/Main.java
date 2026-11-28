package cz.wingit.app;

import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.Properties;

import javax.swing.SwingUtilities;

import cz.wingit.core.DefaultGitService;
import cz.wingit.core.GitService;
import cz.wingit.core.cli.GitCli;
import cz.wingit.core.repo.RepositoryList;
import cz.wingit.ui.Messages;
import cz.wingit.ui.Theme;
import cz.wingit.ui.common.WindowLayout;
import cz.wingit.ui.log.LogWindow;
import cz.wingit.ui.main.MainWindow;
import cz.wingit.ui.settings.Appearance;

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
        Appearance appearance = settings.appearance();
        git.setSshKey(settings.sshKey());

        SwingUtilities.invokeLater(() -> {
            Messages.setLanguage(appearance.language());
            Theme.install(appearance.theme());
            MainWindow window = new MainWindow(git, repositories);
            window.installSettings(appearance, settings::preferences, settings::setPreferences,
                    Restarter.currentCommand().map(command -> () -> restart(settings, window, command)));
            window.setFetchInterval(settings.fetchInterval());
            window.setVersion(version());
            settings.windowLayout().ifPresent(window::applyLayout);
            LogWindow.setLayoutStore(settings::logWindowLayout, layout -> rememberLogLayout(settings, layout));
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
     * {@code jpackage.app-version}; otherwise (jar, IDE) the Maven version from {@code version.properties}.
     * {@code null} if neither is known.
     */
    static String version() {
        String version = System.getProperty("jpackage.app-version");
        return version == null || version.isBlank() ? mavenVersion() : version;
    }

    private static String mavenVersion() {
        try (InputStream in = Main.class.getResourceAsStream("version.properties")) {
            if (in == null) {
                return null;
            }
            Properties properties = new Properties();
            properties.load(in);
            String version = properties.getProperty("version");
            // Unfiltered (e.g. an IDE that skips Maven filtering): no version rather than "${...}".
            return version == null || version.isBlank() || version.startsWith("$") ? null : version;
        } catch (IOException e) {
            return null;
        }
    }

    /**
     * Saves what closing the window would save, starts a new instance and exits.
     *
     * @throws UncheckedIOException if the new instance cannot be started (this one keeps running)
     */
    private static void restart(AppSettings settings, MainWindow window, List<String> command) {
        rememberLayout(settings, window);
        Restarter.launch(command);
        System.exit(0);
    }

    /** Window geometry (also of open log windows) and the selected file, saved when the window closes. */
    private static void rememberLayout(AppSettings settings, MainWindow window) {
        LogWindow.rememberLayouts();
        try {
            settings.setWindowLayout(window.currentLayout());
            settings.setLastSelectedFile(window.selectedFile());
        } catch (UncheckedIOException e) {
            // Not critical: the next start uses the default window size.
            System.err.println("WinGit: " + e.getMessage());
        }
    }

    private static void rememberLogLayout(AppSettings settings, WindowLayout layout) {
        try {
            settings.setLogWindowLayout(layout);
        } catch (UncheckedIOException e) {
            // Not critical: the next log window opens with the default size.
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
