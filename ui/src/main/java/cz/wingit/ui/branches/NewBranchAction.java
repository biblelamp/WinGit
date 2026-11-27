package cz.wingit.ui.branches;

import java.awt.event.ActionEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutionException;

import javax.swing.AbstractAction;
import javax.swing.KeyStroke;
import javax.swing.SwingWorker;

import cz.wingit.core.GitService;
import cz.wingit.core.log.CommitInfo;
import cz.wingit.core.refs.LocalBranch;
import cz.wingit.core.refs.RepositoryRefs;
import cz.wingit.ui.Messages;
import cz.wingit.ui.common.Dialogs;
import cz.wingit.ui.repositories.RepositoriesPane;

/**
 * "New Branch": creates a local branch at the current commit, optionally checking it out.
 * Enabled only while a repository is open.
 */
public final class NewBranchAction extends AbstractAction {

    private static final long serialVersionUID = 1L;

    private final transient GitService git;
    private final RepositoriesPane pane;
    private final transient Runnable afterwards;
    private boolean busy;

    /** @param afterwards runs on the EDT after a branch was created, e.g. to refresh the panes */
    public NewBranchAction(GitService git, RepositoriesPane pane, Runnable afterwards) {
        super(Messages.get("menu.branch.new"));
        putValue(MNEMONIC_KEY, KeyEvent.VK_N);
        putValue(ACCELERATOR_KEY, KeyStroke.getKeyStroke(KeyEvent.VK_B, InputEvent.CTRL_DOWN_MASK));
        this.git = git;
        this.pane = pane;
        this.afterwards = afterwards;
        setEnabled(pane.openedRepository().isPresent());
        pane.addOpenListener(opened -> setEnabled(opened.isPresent()));
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        Optional<Path> opened = pane.openedRepository();
        if (busy || opened.isEmpty()) {
            return;
        }
        Path repo = opened.get();
        busy = true;
        new SwingWorker<Prepared, Void>() {
            @Override
            protected Prepared doInBackground() {
                RepositoryRefs refs = git.refs(repo);
                List<String> existing = refs.localBranches().stream().map(LocalBranch::name).toList();
                String current = refs.localBranches().stream().filter(LocalBranch::current)
                        .map(LocalBranch::name).findFirst().orElse(null);
                List<CommitInfo> head = git.recentCommits(repo, 1);
                return new Prepared(existing,
                        head.isEmpty() ? null : new NewBranchDialog.Source(head.get(0), current));
            }

            @Override
            protected void done() {
                busy = false;
                try {
                    Prepared prepared = get();
                    if (pane.openedRepository().equals(Optional.of(repo))) {
                        NewBranchDialog.show(pane, prepared.existing(), prepared.source())
                                .ifPresent(r -> create(repo, r));
                    }
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                } catch (ExecutionException ex) {
                    Dialogs.showError(pane, Messages.get("newBranch.error.title"), ex);
                }
            }
        }.execute();
    }

    /** What the dialog needs, read in the background. */
    private record Prepared(List<String> existing, NewBranchDialog.Source source) {
    }

    private void create(Path repo, NewBranchDialog.Result request) {
        busy = true;
        new SwingWorker<Void, Void>() {
            @Override
            protected Void doInBackground() {
                git.createBranch(repo, request.name(), request.checkout());
                return null;
            }

            @Override
            protected void done() {
                busy = false;
                try {
                    get();
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                } catch (ExecutionException ex) {
                    Dialogs.showError(pane, Messages.get("newBranch.error.title"), ex);
                }
                afterwards.run();
            }
        }.execute();
    }
}
