package cz.wingit.ui.repositories;

import java.awt.event.ActionEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;

import javax.swing.AbstractAction;
import javax.swing.KeyStroke;

import cz.wingit.ui.Messages;

/**
 * "Close": closes the opened repository. The panes go empty and the repository is no longer
 * watched; it stays in the list and can be opened again. Enabled only while one is open.
 */
public final class CloseRepositoryAction extends AbstractAction {

    private static final long serialVersionUID = 1L;

    private final RepositoriesPane pane;

    public CloseRepositoryAction(RepositoriesPane pane) {
        super(Messages.get("menu.repository.close"));
        putValue(MNEMONIC_KEY, KeyEvent.VK_O);
        putValue(ACCELERATOR_KEY, KeyStroke.getKeyStroke(KeyEvent.VK_W, InputEvent.CTRL_DOWN_MASK));
        this.pane = pane;
        setEnabled(pane.openedRepository().isPresent());
        pane.addOpenListener(opened -> setEnabled(opened.isPresent()));
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        pane.closeOpened();
    }
}
