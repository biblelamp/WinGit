package cz.wingit.ui.reposettings;

import java.awt.Component;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.nio.file.Path;
import java.util.Optional;
import java.util.function.Supplier;

import javax.swing.AbstractAction;

import cz.wingit.core.GitService;
import cz.wingit.ui.Messages;
import cz.wingit.ui.repositories.RepositoriesPane;

/**
 * "Repository Settings…": settings of the repository selected in the list, stored in its own Git
 * configuration (URL of origin, SSH key). Enabled while a repository is selected.
 */
public final class RepositorySettingsAction extends AbstractAction {

    private static final long serialVersionUID = 1L;

    private final transient RepositoriesPane pane;
    private final transient GitService git;
    private final transient Supplier<Optional<Path>> globalKey;

    /** @param globalKey the SSH key chosen in the application settings (shown as what applies otherwise) */
    public RepositorySettingsAction(RepositoriesPane pane, GitService git, Supplier<Optional<Path>> globalKey) {
        super(Messages.get("menu.repository.repoSettings"));
        putValue(MNEMONIC_KEY, KeyEvent.VK_P);
        this.pane = pane;
        this.git = git;
        this.globalKey = globalKey;
        updateEnabled();
        pane.addSelectionListener(this::updateEnabled);
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        pane.selectedRepository().ifPresent(path ->
                RepositorySettingsDialog.show(pane, git, path, globalKey.get()));
    }

    /** Opens the settings of {@code repository} over {@code owner}, e.g. from the commit dialog. */
    public void open(Component owner, Path repository) {
        RepositorySettingsDialog.show(owner, git, repository, globalKey.get());
    }

    private void updateEnabled() {
        setEnabled(pane.selectedRepository().isPresent());
    }
}
