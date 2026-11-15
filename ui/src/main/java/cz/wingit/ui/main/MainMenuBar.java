package cz.wingit.ui.main;

import java.awt.Window;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.WindowEvent;

import javax.swing.Action;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;

import cz.wingit.ui.Messages;

/**
 * Main menu. Only items that actually work are added; menus grow with the features.
 */
public final class MainMenuBar extends JMenuBar {

    private static final long serialVersionUID = 1L;

    public MainMenuBar(Action addOrCreateRepository, Action cloneRepository, Action closeRepository,
                       Action removeRepository, Action commit, Action pull, Action push, Action newBranch,
                       Action renameBranch, Action deleteBranch, Action merge, Action abortMerge,
                       Action... fileActions) {
        JMenu repository = new JMenu(Messages.get("menu.repository"));
        repository.setMnemonic(KeyEvent.VK_R);
        repository.add(addOrCreateRepository);
        repository.add(cloneRepository);
        repository.addSeparator();
        repository.add(closeRepository);
        repository.add(removeRepository);
        repository.addSeparator();
        repository.add(exitItem());
        add(repository);

        JMenu local = new JMenu(Messages.get("menu.local"));
        local.setMnemonic(KeyEvent.VK_L);
        local.add(commit);
        if (fileActions.length > 0) {
            local.addSeparator();
            for (Action action : fileActions) {
                local.add(action);
            }
        }
        add(local);

        JMenu branch = new JMenu(Messages.get("menu.branch"));
        branch.setMnemonic(KeyEvent.VK_B);
        branch.add(newBranch);
        branch.add(renameBranch);
        branch.add(deleteBranch);
        branch.addSeparator();
        branch.add(merge);
        branch.add(abortMerge);
        add(branch);

        JMenu remote = new JMenu(Messages.get("menu.remote"));
        remote.setMnemonic(KeyEvent.VK_M);
        remote.add(pull);
        remote.add(push);
        add(remote);
    }

    private JMenuItem exitItem() {
        JMenuItem exit = new JMenuItem(Messages.get("menu.repository.exit"));
        exit.setMnemonic(KeyEvent.VK_X);
        exit.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_X, InputEvent.ALT_DOWN_MASK));
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
