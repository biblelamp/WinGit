package cz.wingit.ui.repositories;

import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import javax.swing.Action;
import javax.swing.DefaultListModel;
import javax.swing.JComponent;
import javax.swing.JList;
import javax.swing.JPopupMenu;
import javax.swing.KeyStroke;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;

import cz.wingit.core.GitService;
import cz.wingit.core.repo.RepositoryList;
import cz.wingit.ui.Messages;
import cz.wingit.ui.common.Components;
import cz.wingit.ui.common.Dialogs;
import cz.wingit.ui.common.PlaceholderList;
import cz.wingit.ui.common.TitledPane;

/**
 * The user's repositories with their current branch. Branches are read in the background.
 */
public final class RepositoriesPane extends TitledPane {

    private static final long serialVersionUID = 1L;

    private final transient GitService git;
    private final transient RepositoryList repositories;
    private final DefaultListModel<RepositoryEntry> model = new DefaultListModel<>();
    private final JList<RepositoryEntry> list;
    private transient SwingWorker<Map<Path, String>, Void> branchLoader;

    public RepositoriesPane(GitService git, RepositoryList repositories) {
        this(git, repositories, new PlaceholderList<>(Messages.get("repositories.empty")));
    }

    private RepositoriesPane(GitService git, RepositoryList repositories, JList<RepositoryEntry> list) {
        super(Messages.get("repositories.title"), Components.borderlessScroll(list));
        this.git = git;
        this.repositories = repositories;
        this.list = list;
        list.setModel(model);
        list.setCellRenderer(new RepositoryCellRenderer());
        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        reload(null);
    }

    /** Adds a repository to the persistent list and selects it. Call on the EDT. */
    public void addRepository(Path workTree) {
        try {
            repositories.add(workTree);
        } catch (UncheckedIOException e) {
            Dialogs.showError(SwingUtilities.getWindowAncestor(this), Messages.get("addRepo.error.title"), e);
            return;
        }
        reload(workTree);
    }

    /**
     * Removes a repository from the persistent list (not from disk) and selects its neighbour.
     * Call on the EDT.
     */
    public void removeRepository(Path workTree) {
        int index = indexOf(workTree);
        try {
            repositories.remove(workTree);
        } catch (UncheckedIOException e) {
            Dialogs.showError(SwingUtilities.getWindowAncestor(this), Messages.get("removeRepo.error.title"), e);
            reload(null);
            return;
        }
        if (index >= 0) {
            model.remove(index);
            if (!model.isEmpty()) {
                int next = Math.min(index, model.size() - 1);
                list.setSelectedIndex(next);
                list.ensureIndexIsVisible(next);
            }
        }
    }

    /** The selected repository, if any. */
    public Optional<Path> selectedRepository() {
        RepositoryEntry entry = list.getSelectedValue();
        return entry != null ? Optional.of(entry.path()) : Optional.empty();
    }

    /** Notified (on the EDT) whenever the selection changes. */
    public void addSelectionListener(Runnable listener) {
        list.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                listener.run();
            }
        });
    }

    /** Binds the remove action to the Delete key and to the list's context menu. */
    public void installRemoveAction(Action remove) {
        list.getInputMap(JComponent.WHEN_FOCUSED).put(KeyStroke.getKeyStroke(KeyEvent.VK_DELETE, 0), "removeRepository");
        list.getActionMap().put("removeRepository", remove);

        JPopupMenu popup = new JPopupMenu();
        popup.add(remove);
        list.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                maybeShowPopup(e);
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                maybeShowPopup(e);
            }

            private void maybeShowPopup(MouseEvent e) {
                if (!e.isPopupTrigger()) {
                    return;
                }
                int index = list.locationToIndex(e.getPoint());
                if (index < 0 || !list.getCellBounds(index, index).contains(e.getPoint())) {
                    return;
                }
                list.setSelectedIndex(index);
                popup.show(list, e.getX(), e.getY());
            }
        });
    }

    /** Where folder choosers start: the parent of the selected (or first) repository, else the home folder. */
    public Path defaultParentFolder() {
        RepositoryEntry entry = list.getSelectedValue();
        if (entry == null && !model.isEmpty()) {
            entry = model.get(0);
        }
        if (entry != null && entry.path().getParent() != null) {
            return entry.path().getParent();
        }
        return Path.of(System.getProperty("user.home"));
    }

    private void reload(Path toSelect) {
        model.clear();
        for (Path path : repositories.paths()) {
            model.addElement(new RepositoryEntry(path, null));
            if (toSelect != null && RepositoryList.samePath(path, toSelect)) {
                list.setSelectedIndex(model.size() - 1);
                list.ensureIndexIsVisible(model.size() - 1);
            }
        }
        loadBranches();
    }

    private int indexOf(Path path) {
        for (int i = 0; i < model.size(); i++) {
            if (RepositoryList.samePath(model.get(i).path(), path)) {
                return i;
            }
        }
        return -1;
    }

    private void loadBranches() {
        if (branchLoader != null) {
            branchLoader.cancel(false);
        }
        List<Path> paths = repositories.paths();
        branchLoader = new SwingWorker<>() {
            @Override
            protected Map<Path, String> doInBackground() {
                Map<Path, String> branches = new HashMap<>();
                for (Path path : paths) {
                    if (isCancelled()) {
                        break;
                    }
                    git.currentBranch(path).ifPresent(branch -> branches.put(path, branch));
                }
                return branches;
            }

            @Override
            protected void done() {
                if (isCancelled()) {
                    return;
                }
                try {
                    Map<Path, String> branches = get();
                    for (int i = 0; i < model.size(); i++) {
                        RepositoryEntry entry = model.get(i);
                        String branch = branches.get(entry.path());
                        if (branch != null) {
                            model.set(i, entry.withBranch(branch));
                        }
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } catch (Exception e) {
                    // Branch names are decoration only; the list stays usable without them.
                }
            }
        };
        branchLoader.execute();
    }
}
