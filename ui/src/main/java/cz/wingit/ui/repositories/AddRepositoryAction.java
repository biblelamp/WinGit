package cz.wingit.ui.repositories;

import java.awt.Component;
import java.awt.event.ActionEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.ExecutionException;

import javax.swing.AbstractAction;
import javax.swing.JFileChooser;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;

import cz.wingit.core.GitService;
import cz.wingit.ui.Messages;
import cz.wingit.ui.common.Dialogs;

/**
 * "Add or Create": picks a folder and adds it to the repository list. A plain or missing
 * folder can be initialized as a new repository; a subfolder resolves to its repository root.
 */
public final class AddRepositoryAction extends AbstractAction {

    private static final long serialVersionUID = 1L;

    private final transient GitService git;
    private final RepositoriesPane pane;

    public AddRepositoryAction(GitService git, RepositoriesPane pane) {
        super(Messages.get("menu.repository.addOrCreate"));
        putValue(MNEMONIC_KEY, KeyEvent.VK_A);
        putValue(ACCELERATOR_KEY, KeyStroke.getKeyStroke(KeyEvent.VK_O, InputEvent.CTRL_DOWN_MASK));
        this.git = git;
        this.pane = pane;
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        Component parent = SwingUtilities.getWindowAncestor(pane);
        JFileChooser chooser = new JFileChooser(pane.defaultParentFolder().toFile());
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        chooser.setDialogTitle(Messages.get("addRepo.chooser.title"));
        if (chooser.showDialog(parent, Messages.get("addRepo.chooser.approve")) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        File selected = chooser.getSelectedFile();
        if (selected != null) {
            probe(selected.toPath().toAbsolutePath().normalize());
        }
    }

    private void probe(Path dir) {
        new SwingWorker<Optional<Path>, Void>() {
            private boolean exists;

            @Override
            protected Optional<Path> doInBackground() {
                exists = Files.isDirectory(dir);
                return exists ? git.findWorkTreeRoot(dir) : Optional.empty();
            }

            @Override
            protected void done() {
                try {
                    decide(dir, exists, get());
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                } catch (ExecutionException ex) {
                    showError(ex);
                }
            }
        }.execute();
    }

    private void decide(Path dir, boolean exists, Optional<Path> root) {
        Component parent = SwingUtilities.getWindowAncestor(pane);
        String title = Messages.get("addRepo.title");
        if (root.isPresent() && root.get().equals(dir)) {
            pane.addRepository(dir);
        } else if (root.isPresent()) {
            if (Dialogs.confirm(parent, title, Messages.format("addRepo.inside.message", dir, root.get()))) {
                pane.addRepository(root.get());
            }
        } else if (exists) {
            if (Dialogs.confirm(parent, title, Messages.format("addRepo.init.message", dir))) {
                initAndAdd(dir, false);
            }
        } else if (Dialogs.confirm(parent, title, Messages.format("addRepo.missing.message", dir))) {
            initAndAdd(dir, true);
        }
    }

    private void initAndAdd(Path dir, boolean create) {
        new SwingWorker<Void, Void>() {
            @Override
            protected Void doInBackground() throws Exception {
                if (create) {
                    Files.createDirectories(dir);
                }
                git.init(dir);
                return null;
            }

            @Override
            protected void done() {
                try {
                    get();
                    pane.addRepository(dir);
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                } catch (ExecutionException ex) {
                    showError(ex);
                }
            }
        }.execute();
    }

    private void showError(Exception ex) {
        Dialogs.showError(SwingUtilities.getWindowAncestor(pane), Messages.get("addRepo.error.title"), ex);
    }
}
