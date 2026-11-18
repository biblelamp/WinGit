package cz.wingit.ui.remote;

import java.awt.event.ActionEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;

import javax.swing.AbstractAction;
import javax.swing.KeyStroke;

import cz.wingit.core.GitService;
import cz.wingit.ui.Messages;
import cz.wingit.ui.repositories.RepositoriesPane;

/**
 * "Fetch" / "Pull" / "Push" for the opened repository. Enabled only while a repository is open.
 */
public final class RemoteAction extends AbstractAction {

    private static final long serialVersionUID = 1L;

    private final transient GitService git;
    private final RepositoriesPane pane;
    private final RemoteOperation.Kind kind;
    private final transient Runnable afterwards;

    /**
     * @param afterwards runs on the EDT when the operation finished (also after a failure), e.g. to refresh
     */
    public static RemoteAction fetch(GitService git, RepositoriesPane pane, Runnable afterwards) {
        RemoteAction action = new RemoteAction(git, pane, RemoteOperation.Kind.FETCH, afterwards,
                Messages.get("menu.remote.fetch"));
        action.putValue(MNEMONIC_KEY, KeyEvent.VK_F);
        return action;
    }

    /** See {@link #fetch}. */
    public static RemoteAction pull(GitService git, RepositoriesPane pane, Runnable afterwards) {
        RemoteAction action = new RemoteAction(git, pane, RemoteOperation.Kind.PULL, afterwards,
                Messages.get("menu.remote.pull"));
        action.putValue(MNEMONIC_KEY, KeyEvent.VK_L);
        action.putValue(ACCELERATOR_KEY, KeyStroke.getKeyStroke(KeyEvent.VK_T, InputEvent.CTRL_DOWN_MASK));
        return action;
    }

    /** See {@link #pull}. */
    public static RemoteAction push(GitService git, RepositoriesPane pane, Runnable afterwards) {
        RemoteAction action = new RemoteAction(git, pane, RemoteOperation.Kind.PUSH, afterwards,
                Messages.get("menu.remote.push"));
        action.putValue(MNEMONIC_KEY, KeyEvent.VK_P);
        action.putValue(ACCELERATOR_KEY, KeyStroke.getKeyStroke(KeyEvent.VK_K,
                InputEvent.CTRL_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK));
        return action;
    }

    private RemoteAction(GitService git, RepositoriesPane pane, RemoteOperation.Kind kind, Runnable afterwards,
                         String name) {
        super(name);
        this.git = git;
        this.pane = pane;
        this.kind = kind;
        this.afterwards = afterwards;
        setEnabled(pane.openedRepository().isPresent());
        pane.addOpenListener(opened -> setEnabled(opened.isPresent()));
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        pane.openedRepository().ifPresent(repo -> {
            new RemoteOperation(git, pane).run(kind, repo);
            afterwards.run();
        });
    }
}
