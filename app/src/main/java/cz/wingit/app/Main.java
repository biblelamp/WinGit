package cz.wingit.app;

import javax.swing.SwingUtilities;

import cz.wingit.ui.Theme;
import cz.wingit.ui.main.MainWindow;

/**
 * Application entry point.
 */
public final class Main {

    private Main() {
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            Theme.install(Theme.Mode.LIGHT);
            new MainWindow().setVisible(true);
        });
    }
}
