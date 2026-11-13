package cz.wingit.ui.main;

import java.awt.Dimension;

import javax.swing.JFrame;
import javax.swing.WindowConstants;

import cz.wingit.ui.Messages;

/**
 * Top-level application window. Panes will be added here later.
 */
public final class MainWindow extends JFrame {

    private static final long serialVersionUID = 1L;

    public MainWindow() {
        super(Messages.get("mainWindow.title"));
        setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        setMinimumSize(new Dimension(640, 400));
        setSize(1200, 800);
        setLocationByPlatform(true);
    }
}
