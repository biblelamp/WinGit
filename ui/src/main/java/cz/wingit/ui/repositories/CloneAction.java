package cz.wingit.ui.repositories;

import java.awt.event.ActionEvent;

import javax.swing.AbstractAction;

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
        this.git = git;
        this.pane = pane;
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        CloneDialog.show(pane, git, pane.defaultParentFolder()).ifPresent(pane::addRepository);
    }
}
