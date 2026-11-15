package cz.wingit.ui.branches;

import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.ExecutionException;

import javax.swing.AbstractAction;
import javax.swing.JCheckBox;
import javax.swing.SwingWorker;

import cz.wingit.core.GitBranchNotMergedException;
import cz.wingit.core.GitService;
import cz.wingit.ui.Messages;
import cz.wingit.ui.common.Dialogs;
import cz.wingit.ui.remote.RemoteOperation;

/**
 * "Delete Branch" for the branch selected in the Branches pane, always after a confirmation:
 * <ul>
 * <li>a local branch (not the checked-out one); if it tracks a remote branch, the confirmation offers
 * to delete that one on the server too. A branch that is not fully merged needs a second
 * confirmation before it is force-deleted;</li>
 * <li>a remote branch: it is deleted on the server ({@code git push --delete}), with sign-in if needed.</li>
 * </ul>
 */
public final class DeleteBranchAction extends AbstractAction {

    private static final long serialVersionUID = 1L;

    private final transient GitService git;
    private final BranchesPane pane;
    private final transient Runnable afterwards;
    private boolean busy;

    /** @param afterwards runs on the EDT after a deletion attempt, e.g. to refresh the panes */
    public DeleteBranchAction(GitService git, BranchesPane pane, Runnable afterwards) {
        super(Messages.get("menu.branch.delete"));
        putValue(MNEMONIC_KEY, KeyEvent.VK_D);
        this.git = git;
        this.pane = pane;
        this.afterwards = afterwards;
        updateEnabled();
        pane.addSelectionListener(this::updateEnabled);
    }

    private void updateEnabled() {
        setEnabled(!busy && (pane.selectedLocalBranch().isPresent() || pane.selectedRemoteBranch().isPresent()));
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        Optional<Path> repo = pane.repository();
        if (busy || repo.isEmpty()) {
            return;
        }
        Optional<BranchItem.Target> remote = pane.selectedRemoteBranch();
        if (remote.isPresent()) {
            BranchItem.Target target = remote.get();
            if (Dialogs.confirmWarning(pane, Messages.get("deleteBranch.remote.title"),
                    Messages.format("deleteBranch.remote.confirm", target.remote(), target.branch()))) {
                deleteOnServer(repo.get(), target);
                afterwards.run();
            }
            return;
        }
        Optional<String> local = pane.selectedLocalBranch();
        if (local.isPresent()) {
            confirmLocal(repo.get(), local.get());
        }
    }

    private void confirmLocal(Path repo, String branch) {
        Optional<BranchItem.Target> upstream = pane.upstreamOf(branch);
        JCheckBox alsoRemote = upstream.map(u -> new JCheckBox(
                Messages.format("deleteBranch.alsoRemote", u.remote(), u.branch()))).orElse(null);
        String question = Messages.format("deleteBranch.confirm", branch);
        Object message = alsoRemote == null ? question : new Object[] {question, alsoRemote};
        if (!Dialogs.confirmWarning(pane, Messages.get("deleteBranch.title"), message)) {
            return;
        }
        if (alsoRemote != null && alsoRemote.isSelected()) {
            if (!Dialogs.confirmWarning(pane, Messages.get("deleteBranch.remote.title"),
                    Messages.format("deleteBranch.remote.confirm", upstream.get().remote(), upstream.get().branch()))
                    || !deleteOnServer(repo, upstream.get())) {
                afterwards.run();
                return; // keep the local branch if the server one could not be deleted
            }
        }
        deleteLocal(repo, branch, false);
    }

    /** Deletes the branch on the server with progress and sign-in; returns whether it worked. */
    private boolean deleteOnServer(Path repo, BranchItem.Target target) {
        busy = true;
        updateEnabled();
        try {
            return new RemoteOperation(git, pane).run(
                    Messages.format("deleteBranch.remote.progress", target.remote(), target.branch()),
                    Messages.format("deleteBranch.remote.error.title", target.remote(), target.branch()),
                    repo,
                    (credentials, progress) -> git.deleteRemoteBranch(repo, target.remote(), target.branch(),
                            credentials, progress));
        } finally {
            busy = false;
            updateEnabled();
        }
    }

    private void deleteLocal(Path repo, String branch, boolean force) {
        busy = true;
        updateEnabled();
        new SwingWorker<Void, Void>() {
            @Override
            protected Void doInBackground() {
                git.deleteBranch(repo, branch, force);
                return null;
            }

            @Override
            protected void done() {
                busy = false;
                updateEnabled();
                try {
                    get();
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                } catch (ExecutionException ex) {
                    if (ex.getCause() instanceof GitBranchNotMergedException) {
                        if (Dialogs.confirmWarning(pane, Messages.get("deleteBranch.title"),
                                Messages.format("deleteBranch.notMerged", branch))) {
                            deleteLocal(repo, branch, true);
                            return;
                        }
                    } else {
                        Dialogs.showError(pane, Messages.format("deleteBranch.error.title", branch), ex);
                    }
                }
                afterwards.run();
            }
        }.execute();
    }
}
