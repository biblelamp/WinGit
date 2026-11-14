package cz.wingit.ui.repositories;

import java.awt.event.ActionEvent;

import javax.swing.AbstractAction;
import javax.swing.SwingUtilities;

import cz.wingit.ui.Messages;
import cz.wingit.ui.common.Dialogs;

/**
 * "Remove from List": forgets the selected repository after confirmation. Files on disk stay.
 * Enabled only while a repository is selected.
 */
public final class RemoveRepositoryAction extends AbstractAction {

    private static final long serialVersionUID = 1L;

    private final RepositoriesPane pane;

    public RemoveRepositoryAction(RepositoriesPane pane) {
        super(Messages.get("menu.repository.remove"));
        this.pane = pane;
        updateEnabled();
        pane.addSelectionListener(this::updateEnabled);
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        pane.selectedRepository().ifPresent(path -> {
            String name = path.getFileName() != null ? path.getFileName().toString() : path.toString();
            if (Dialogs.confirm(SwingUtilities.getWindowAncestor(pane), Messages.get("removeRepo.title"),
                    Messages.format("removeRepo.message", name, path))) {
                pane.removeRepository(path);
            }
        });
    }

    private void updateEnabled() {
        setEnabled(pane.selectedRepository().isPresent());
    }
}
