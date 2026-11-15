package cz.wingit.ui.branches;

import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.ExecutionException;

import javax.swing.AbstractAction;
import javax.swing.SwingWorker;

import cz.wingit.core.GitService;
import cz.wingit.ui.Messages;
import cz.wingit.ui.common.Dialogs;
import cz.wingit.ui.remote.RemoteOperation;

/**
 * "Rename Branch" for the branch selected in the Branches pane:
 * <ul>
 * <li>a local branch (also the checked-out one); if it tracks a server branch, the dialog offers to
 * rename that one to the same new name as well;</li>
 * <li>a remote branch: renamed on the server after a confirmation, with sign-in if needed.</li>
 * </ul>
 */
public final class RenameBranchAction extends AbstractAction {

    private static final long serialVersionUID = 1L;

    private final transient GitService git;
    private final BranchesPane pane;
    private final transient Runnable afterwards;
    private boolean busy;

    /** @param afterwards runs on the EDT after a rename attempt, e.g. to refresh the panes */
    public RenameBranchAction(GitService git, BranchesPane pane, Runnable afterwards) {
        super(Messages.get("menu.branch.rename"));
        putValue(MNEMONIC_KEY, KeyEvent.VK_R);
        this.git = git;
        this.pane = pane;
        this.afterwards = afterwards;
        updateEnabled();
        pane.addSelectionListener(this::updateEnabled);
    }

    private void updateEnabled() {
        setEnabled(!busy && (pane.selectedAnyLocalBranch().isPresent() || pane.selectedRemoteBranch().isPresent()));
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        Optional<Path> repo = pane.repository();
        if (busy || repo.isEmpty()) {
            return;
        }
        Optional<BranchItem.Target> remote = pane.selectedRemoteBranch();
        if (remote.isPresent()) {
            renameRemoteOnly(repo.get(), remote.get());
            return;
        }
        Optional<String> local = pane.selectedAnyLocalBranch();
        if (local.isPresent()) {
            renameLocal(repo.get(), local.get());
        }
    }

    private void renameRemoteOnly(Path repo, BranchItem.Target target) {
        Optional<RenameBranchDialog.Result> entered = RenameBranchDialog.show(pane, target.branch(),
                pane.remoteBranchNames(target.remote()),
                Messages.format("renameBranch.remote.header", target.remote(), target.branch()),
                Messages.get("renameBranch.remote.description"), null);
        if (entered.isPresent() && confirmServer(target, entered.get().newName())) {
            renameOnServer(repo, target, entered.get().newName());
            afterwards.run();
        }
    }

    private void renameLocal(Path repo, String oldName) {
        Optional<BranchItem.Target> upstream = pane.upstreamOf(oldName);
        Optional<RenameBranchDialog.Result> entered = RenameBranchDialog.show(pane, oldName, pane.localBranchNames(),
                Messages.format("renameBranch.header", oldName), Messages.get("renameBranch.description"),
                upstream.map(u -> Messages.format("renameBranch.alsoRemote", u.remote(), u.branch())).orElse(null));
        if (entered.isEmpty()) {
            return;
        }
        String newName = entered.get().newName();
        if (entered.get().option() && upstream.isPresent()) {
            if (!confirmServer(upstream.get(), newName) || !renameOnServer(repo, upstream.get(), newName)) {
                afterwards.run();
                return; // keep the local name if the server branch could not be renamed
            }
        }
        renameLocalBranch(repo, oldName, newName);
    }

    private boolean confirmServer(BranchItem.Target target, String newName) {
        return Dialogs.confirmWarning(pane, Messages.get("renameBranch.remote.title"),
                Messages.format("renameBranch.remote.confirm", target.remote(), target.branch(), newName));
    }

    /** Renames the branch on the server with progress and sign-in; returns whether it worked. */
    private boolean renameOnServer(Path repo, BranchItem.Target target, String newName) {
        busy = true;
        updateEnabled();
        try {
            return new RemoteOperation(git, pane).run(
                    Messages.format("renameBranch.remote.progress", target.remote(), target.branch()),
                    Messages.format("renameBranch.remote.error.title", target.remote(), target.branch()),
                    repo,
                    (credentials, progress) -> git.renameRemoteBranch(repo, target.remote(), target.branch(),
                            newName, credentials, progress));
        } finally {
            busy = false;
            updateEnabled();
        }
    }

    private void renameLocalBranch(Path repo, String oldName, String newName) {
        busy = true;
        updateEnabled();
        new SwingWorker<Void, Void>() {
            @Override
            protected Void doInBackground() {
                git.renameBranch(repo, oldName, newName);
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
                    Dialogs.showError(pane, Messages.format("renameBranch.error.title", oldName), ex);
                }
                afterwards.run();
            }
        }.execute();
    }
}
