package cz.wingit.ui.repositories;

import java.awt.event.ActionEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;

import javax.swing.AbstractAction;
import javax.swing.KeyStroke;

import cz.wingit.core.GitService;
import cz.wingit.ui.Messages;

/**
 * "Clone": asks for URL and target folder, clones, and adds the result to the repository list.
 */
public final class CloneAction extends AbstractAction {

    private static final long serialVersionUID = 1L;

    private final transient GitService git;
    private final RepositoriesPane pane;

    public CloneAction(GitService git, RepositoriesPane pane) {
        super(Messages.get("menu.repository.clone"));
        putValue(MNEMONIC_KEY, KeyEvent.VK_C);
        putValue(ACCELERATOR_KEY, KeyStroke.getKeyStroke(KeyEvent.VK_O,
                InputEvent.CTRL_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK));
        this.git = git;
        this.pane = pane;
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        CloneDialog.show(pane, git, pane.defaultParentFolder()).ifPresent(pane::addRepository);
    }
}
