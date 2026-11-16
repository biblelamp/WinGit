package cz.wingit.ui.tags;

import java.awt.Component;
import java.awt.event.ActionEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.function.Supplier;

import javax.swing.AbstractAction;
import javax.swing.KeyStroke;
import javax.swing.SwingWorker;

import cz.wingit.core.GitService;
import cz.wingit.core.log.CommitInfo;
import cz.wingit.ui.Messages;
import cz.wingit.ui.common.Dialogs;
import cz.wingit.ui.remote.RemoteOperation;
import cz.wingit.ui.repositories.RepositoriesPane;

/**
 * "New Tag": asks for a tag on a commit (the current one, or the one picked in the History pane),
 * creates it and pushes it if the user wants to. Enabled while a repository is open.
 */
public final class NewTagAction extends AbstractAction {

    private static final long serialVersionUID = 1L;

    private final transient GitService git;
    private final RepositoriesPane pane;
    private final transient Supplier<Optional<CommitInfo>> commit;
    private final transient Runnable afterwards;
    private boolean busy;

    /**
     * Tags the current commit (HEAD).
     *
     * @param afterwards runs on the EDT after a tag was created (and pushed), e.g. to refresh the panes
     */
    public static NewTagAction onHead(GitService git, RepositoriesPane pane, Runnable afterwards) {
        NewTagAction action = new NewTagAction(git, pane, Optional::empty, afterwards);
        action.putValue(ACCELERATOR_KEY, KeyStroke.getKeyStroke(KeyEvent.VK_T,
                InputEvent.CTRL_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK));
        return action;
    }

    /**
     * Tags the commit {@code commit} supplies (e.g. the one selected in the History pane), HEAD if none.
     * See {@link #onHead}.
     */
    public static NewTagAction onCommit(GitService git, RepositoriesPane pane, Supplier<Optional<CommitInfo>> commit,
                                        Runnable afterwards) {
        return new NewTagAction(git, pane, commit, afterwards);
    }

    private NewTagAction(GitService git, RepositoriesPane pane, Supplier<Optional<CommitInfo>> commit,
                         Runnable afterwards) {
        super(Messages.get("menu.branch.newTag"));
        putValue(MNEMONIC_KEY, KeyEvent.VK_T);
        this.git = git;
        this.pane = pane;
        this.commit = commit;
        this.afterwards = afterwards;
        setEnabled(pane.openedRepository().isPresent());
        pane.addOpenListener(opened -> setEnabled(opened.isPresent()));
    }

    /** What the dialog needs, read in the background; {@code commit} is null without commits. */
    private record Prepared(CommitInfo commit, List<String> tags, String remote) {
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        Optional<Path> opened = pane.openedRepository();
        if (busy || opened.isEmpty()) {
            return;
        }
        Path repo = opened.get();
        Optional<CommitInfo> chosen = commit.get();
        busy = true;
        new SwingWorker<Prepared, Void>() {
            @Override
            protected Prepared doInBackground() {
                CommitInfo target = chosen.orElseGet(() -> git.recentCommits(repo, 1).stream().findFirst().orElse(null));
                return new Prepared(target, git.refs(repo).tags(), git.defaultRemote(repo).orElse(null));
            }

            @Override
            protected void done() {
                busy = false;
                try {
                    Prepared prepared = get();
                    if (!pane.openedRepository().equals(Optional.of(repo))) {
                        return;
                    }
                    if (prepared.commit() == null) {
                        Dialogs.showInfo(pane, Messages.get("newTag.title"), Messages.get("newTag.noCommit"));
                        return;
                    }
                    NewTagDialog.show(pane, prepared.commit(), prepared.tags(), prepared.remote())
                            .ifPresent(r -> create(repo, prepared, r));
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                } catch (ExecutionException ex) {
                    Dialogs.showError(pane, Messages.get("newTag.error.title"), ex);
                }
            }
        }.execute();
    }

    private void create(Path repo, Prepared prepared, NewTagDialog.Result request) {
        busy = true;
        new SwingWorker<Void, Void>() {
            @Override
            protected Void doInBackground() {
                git.createTag(repo, request.name(), prepared.commit().id(), request.message());
                return null;
            }

            @Override
            protected void done() {
                busy = false;
                try {
                    get();
                    afterwards.run();
                    if (request.push()) {
                        pushTag(git, pane, repo, prepared.remote(), request.name());
                        afterwards.run();
                    }
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                } catch (ExecutionException ex) {
                    Dialogs.showError(pane, Messages.format("newTag.error.titleFor", request.name()), ex);
                    afterwards.run();
                }
            }
        }.execute();
    }

    /** Pushes tag {@code name} to {@code remote} with progress and sign-in; returns whether it worked. */
    static boolean pushTag(GitService git, Component parent, Path repo, String remote, String name) {
        return new RemoteOperation(git, parent).run(
                Messages.format("pushTag.progress", name, remote),
                Messages.format("pushTag.error.title", name),
                repo,
                (credentials, progress) -> git.pushTag(repo, remote, name, credentials, progress));
    }
}
