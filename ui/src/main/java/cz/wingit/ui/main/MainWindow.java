package cz.wingit.ui.main;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.GraphicsEnvironment;
import java.awt.Rectangle;

import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.WindowConstants;

import cz.wingit.core.GitService;
import cz.wingit.core.repo.RepositoryList;
import cz.wingit.ui.Messages;
import cz.wingit.ui.repositories.AddRepositoryAction;
import cz.wingit.ui.repositories.CloneAction;
import cz.wingit.ui.repositories.RemoveRepositoryAction;
import cz.wingit.ui.repositories.RepositoriesPane;

/**
 * Top-level application window: menu, toolbar, panes, status bar.
 */
public final class MainWindow extends JFrame {

    private static final long serialVersionUID = 1L;

    public MainWindow(GitService git, RepositoryList repositories) {
        super(Messages.get("mainWindow.title"));
        setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);

        RepositoriesPane repositoriesPane = new RepositoriesPane(git, repositories);
        RemoveRepositoryAction removeRepository = new RemoveRepositoryAction(repositoriesPane);
        repositoriesPane.installRemoveAction(removeRepository);
        setJMenuBar(new MainMenuBar(
                new AddRepositoryAction(git, repositoriesPane),
                new CloneAction(git, repositoriesPane),
                removeRepository));
        setContentPane(createContent(repositoriesPane));

        setMinimumSize(new Dimension(640, 400));
        setSize(fitToScreen(1280, 820));
        setLocationRelativeTo(null);
    }

    static JPanel createContent(RepositoriesPane repositoriesPane) {
        JPanel root = new JPanel(new BorderLayout());
        root.add(new MainToolBar(), BorderLayout.NORTH);
        root.add(new MainContent(repositoriesPane), BorderLayout.CENTER);
        root.add(new StatusBar(), BorderLayout.SOUTH);
        return root;
    }

    /** Preferred size, shrunk to 90 % of the usable screen area (taskbar excluded) if needed. */
    private static Dimension fitToScreen(int width, int height) {
        Rectangle usable = GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds();
        return new Dimension(
                Math.min(width, (int) (usable.width * 0.9)),
                Math.min(height, (int) (usable.height * 0.9)));
    }
}
