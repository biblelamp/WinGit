package cz.wingit.ui.log;

import java.awt.Component;
import java.awt.event.ActionEvent;
import java.util.Optional;

import javax.swing.AbstractAction;

import cz.wingit.core.GitService;
import cz.wingit.ui.Messages;
import cz.wingit.ui.repositories.RepositoriesPane;

/** "History": opens the log window of the opened repository. Enabled while a repository is open. */
public final class LogAction extends AbstractAction {

    private static final long serialVersionUID = 1L;

    private final transient GitService git;
    private final RepositoriesPane pane;
    private final Component owner;

    public LogAction(GitService git, RepositoriesPane pane, Component owner) {
        super(Messages.get("toolbar.history"));
        this.git = git;
        this.pane = pane;
        this.owner = owner;
        setEnabled(pane.openedRepository().isPresent());
        pane.addOpenListener(opened -> setEnabled(opened.isPresent()));
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        pane.openedRepository().ifPresent(repo -> LogWindow.open(owner, git, repo, Optional.empty()));
    }
}
