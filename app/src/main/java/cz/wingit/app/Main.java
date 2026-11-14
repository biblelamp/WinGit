package cz.wingit.app;

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
            settings.lastOpenedRepository().ifPresent(window::reopen);
            window.addOpenListener(opened -> rememberOpened(settings, opened));
            window.setVisible(true);
        });
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
