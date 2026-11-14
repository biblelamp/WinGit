package cz.wingit.app;

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

        SwingUtilities.invokeLater(() -> {
            Theme.install(Theme.Mode.LIGHT);
            new MainWindow(git, repositories).setVisible(true);
        });
    }
}
