package cz.wingit.ui.main;

import java.awt.Window;
import java.awt.event.KeyEvent;
import java.awt.event.WindowEvent;

import javax.swing.Action;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.SwingUtilities;

import cz.wingit.ui.Messages;

/**
 * Main menu. Only items that actually work are added; menus grow with the features.
 */
public final class MainMenuBar extends JMenuBar {

    private static final long serialVersionUID = 1L;

    public MainMenuBar(Action addOrCreateRepository, Action cloneRepository, Action removeRepository) {
        JMenu repository = new JMenu(Messages.get("menu.repository"));
        repository.setMnemonic(KeyEvent.VK_R);
        repository.add(addOrCreateRepository);
        repository.add(cloneRepository);
        repository.add(removeRepository);
        repository.addSeparator();
        repository.add(exitItem());
        add(repository);
    }

    private JMenuItem exitItem() {
        JMenuItem exit = new JMenuItem(Messages.get("menu.repository.exit"));
        exit.addActionListener(e -> {
            Window window = SwingUtilities.getWindowAncestor(this);
            if (window != null) {
                // Same path as the close button, so window listeners (e.g. saving state) run.
                window.dispatchEvent(new WindowEvent(window, WindowEvent.WINDOW_CLOSING));
            }
        });
        return exit;
    }
}
