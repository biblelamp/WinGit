package cz.wingit.ui.repositories;

import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Predicate;

import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.DefaultListModel;
import javax.swing.JComponent;
import javax.swing.JList;
import javax.swing.JPopupMenu;
import javax.swing.KeyStroke;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;

import cz.wingit.core.GitException;
import cz.wingit.core.GitService;
import cz.wingit.core.repo.RepositoryList;
import cz.wingit.ui.Messages;
import cz.wingit.ui.common.Components;
import cz.wingit.ui.common.Dialogs;
import cz.wingit.ui.common.PlaceholderList;
import cz.wingit.ui.common.TitledPane;

/**
 * The user's repositories with their current branch and an icon that turns red when the
 * working tree has changes. Both are read in the background: for all repositories on start and
 * when {@link #refreshAll()} is called; the opened repository is kept current by the Files pane
 * via {@link #updateChanged(Path, boolean)}. A single click only selects a repository; double-click or Enter opens it (shown in bold),
 * which is what loads its data in the other panes.
 */
public final class RepositoriesPane extends TitledPane {

    private static final long serialVersionUID = 1L;

    private final transient GitService git;
    private final transient RepositoryList repositories;
    private final DefaultListModel<RepositoryEntry> model = new DefaultListModel<>();
    private final JList<RepositoryEntry> list;
    private final JPopupMenu popup = new JPopupMenu();
    /** When the "has changes" flag of a path was last set; older background results don't overwrite it. */
    private final transient Map<Path, Long> changedStamps = new HashMap<>();
    private boolean fullRefreshRunning;
    private transient Path opened;
    private transient Predicate<Path> confirmRemoveMissing = this::askRemoveMissing;
    private final transient List<Consumer<Optional<Path>>> openListeners = new ArrayList<>();

    public RepositoriesPane(GitService git, RepositoryList repositories) {
        this(git, repositories, new PlaceholderList<>(Messages.get("repositories.empty")));
    }

    private RepositoriesPane(GitService git, RepositoryList repositories, JList<RepositoryEntry> list) {
        super(Messages.get("repositories.title"), Components.borderlessScroll(list));
        this.git = git;
        this.repositories = repositories;
        this.list = list;
        list.setModel(model);
        list.setCellRenderer(new RepositoryCellRenderer(() -> opened));
        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        installOpenGestures();
        reload(null);
    }

    /** Adds a repository to the persistent list, selects and opens it. Call on the EDT. */
    public void addRepository(Path workTree) {
        try {
            repositories.add(workTree);
        } catch (UncheckedIOException e) {
            Dialogs.showError(SwingUtilities.getWindowAncestor(this), Messages.get("addRepo.error.title"), e);
            return;
        }
        reload(workTree);
        open(workTree); // a just added, created or cloned repository is opened right away
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
        if (opened != null && RepositoryList.samePath(opened, workTree)) {
            setOpened(null);
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

    /**
     * Opens the selected repository (same as double-click / Enter). If its folder no longer exists,
     * it is marked as missing and the user is offered to remove it from the list. Call on the EDT.
     */
    public void openSelected() {
        int index = list.getSelectedIndex();
        if (index < 0) {
            return;
        }
        Path path = model.get(index).path();
        if (Files.isDirectory(path)) {
            setOpened(path);
            return;
        }
        markMissing(index);
        if (confirmRemoveMissing.test(path)) {
            removeRepository(path);
        }
    }

    /** Replaces the "remove missing repository?" question (for tests). */
    void setConfirmRemoveMissing(Predicate<Path> confirm) {
        confirmRemoveMissing = confirm;
    }

    private boolean askRemoveMissing(Path path) {
        String name = path.getFileName() != null ? path.getFileName().toString() : path.toString();
        return Dialogs.confirm(SwingUtilities.getWindowAncestor(this), Messages.get("missingRepo.title"),
                Messages.format("missingRepo.message", name, path));
    }

    private void markMissing(int index) {
        RepositoryEntry entry = model.get(index);
        if (!entry.missing()) {
            model.set(index, entry.withMissing(true));
        }
    }

    /**
     * Selects and opens {@code workTree} if it is in the list (e.g. to restore the last session).
     * A repository whose folder is gone is only selected and marked as missing, without asking.
     *
     * @return {@code false} if the repository is not in the list or its folder is missing
     */
    public boolean open(Path workTree) {
        int index = indexOf(workTree);
        if (index < 0) {
            return false;
        }
        list.setSelectedIndex(index);
        list.ensureIndexIsVisible(index);
        Path path = model.get(index).path();
        if (!Files.isDirectory(path)) {
            markMissing(index);
            return false;
        }
        setOpened(path);
        return true;
    }

    /**
     * Closes the opened repository: the panes are cleared and its file watcher stops.
     * The repository stays in the list. Does nothing if none is open. Call on the EDT.
     */
    public void closeOpened() {
        if (opened != null) {
            setOpened(null);
        }
    }

    /** The repository whose data is shown, if any. */
    public Optional<Path> openedRepository() {
        return Optional.ofNullable(opened);
    }

    /** Notified (on the EDT) when a repository is opened, or with empty when the opened one is closed or removed. */
    public void addOpenListener(Consumer<Optional<Path>> listener) {
        openListeners.add(listener);
    }

    /**
     * Re-reads branch and "has changes" of all repositories in the background (e.g. when the window
     * gets focus again). Skipped while such a refresh is already running.
     */
    public void refreshAll() {
        if (!fullRefreshRunning) {
            loadInfo(repositories.paths(), true);
        }
    }

    /** All repositories of the list, in list order. */
    public List<Path> paths() {
        return repositories.paths();
    }

    /** Re-reads the branch and the commits to push and pull of one repository (e.g. after a checkout or fetch). */
    public void refreshBranch(Path workTree) {
        int index = indexOf(workTree);
        if (index >= 0) {
            loadInfo(List.of(model.get(index).path()), false);
        }
    }

    /** Sets the "has changes" flag from a status that was read elsewhere. Call on the EDT. */
    public void updateChanged(Path workTree, boolean changed) {
        int index = indexOf(workTree);
        if (index < 0) {
            return;
        }
        RepositoryEntry entry = model.get(index);
        changedStamps.put(entry.path(), System.nanoTime());
        if (!Boolean.valueOf(changed).equals(entry.changed())) {
            model.set(index, entry.withChanged(changed));
        }
    }

    /** Notified (on the EDT) whenever the selection changes. */
    public void addSelectionListener(Runnable listener) {
        list.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                listener.run();
            }
        });
    }

    /** Adds {@code action} to the list's context menu (below the remove action, if installed). */
    public void addContextAction(Action action) {
        if (popup.getComponentCount() > 0) {
            popup.addSeparator();
        }
        popup.add(action);
    }

    /** Binds the remove action to the Delete key and to the list's context menu. */
    public void installRemoveAction(Action remove) {
        list.getInputMap(JComponent.WHEN_FOCUSED).put(KeyStroke.getKeyStroke(KeyEvent.VK_DELETE, 0), "removeRepository");
        list.getActionMap().put("removeRepository", remove);

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

    /** Rows as currently shown (for tests). */
    List<RepositoryEntry> entries() {
        List<RepositoryEntry> result = new ArrayList<>();
        for (int i = 0; i < model.size(); i++) {
            result.add(model.get(i));
        }
        return result;
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
            model.addElement(RepositoryEntry.unknown(path));
            if (toSelect != null && RepositoryList.samePath(path, toSelect)) {
                list.setSelectedIndex(model.size() - 1);
                list.ensureIndexIsVisible(model.size() - 1);
            }
        }
        fullRefreshRunning = false;
        loadInfo(repositories.paths(), true);
    }

    private void setOpened(Path path) {
        opened = path;
        list.repaint();
        Optional<Path> value = Optional.ofNullable(path);
        openListeners.forEach(listener -> listener.accept(value));
    }

    private void installOpenGestures() {
        list.getInputMap(JComponent.WHEN_FOCUSED).put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), "openRepository");
        list.getActionMap().put("openRepository", new AbstractAction() {
            private static final long serialVersionUID = 1L;

            @Override
            public void actionPerformed(ActionEvent e) {
                openSelected();
            }
        });
        list.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() != 2 || !SwingUtilities.isLeftMouseButton(e)) {
                    return;
                }
                int index = list.locationToIndex(e.getPoint());
                if (index >= 0 && list.getCellBounds(index, index).contains(e.getPoint())) {
                    list.setSelectedIndex(index);
                    openSelected();
                }
            }
        });
    }

    private int indexOf(Path path) {
        for (int i = 0; i < model.size(); i++) {
            if (RepositoryList.samePath(model.get(i).path(), path)) {
                return i;
            }
        }
        return -1;
    }

    /** Branch, "has changes", unpushed and unpulled commits of one repository; {@code null} parts are unknown. */
    private record Info(String branch, Boolean changed, boolean missing, Integer unpushed, Integer unpulled) {
    }

    /** Unpushed and unpulled commits are counted up to this number (shown as "999+" beyond). */
    static final int MAX_UNPUSHED = 1000;

    /** Reads branch (and optionally "has changes") of {@code paths} in the background and applies it. */
    private void loadInfo(List<Path> paths, boolean withStatus) {
        long started = System.nanoTime();
        if (withStatus) {
            fullRefreshRunning = true;
        }
        new SwingWorker<Map<Path, Info>, Void>() {
            @Override
            protected Map<Path, Info> doInBackground() {
                Map<Path, Info> result = new HashMap<>();
                for (Path path : paths) {
                    if (!Files.isDirectory(path)) {
                        result.put(path, new Info(null, null, true, null, null));
                        continue;
                    }
                    String branch = git.currentBranch(path).orElse(null);
                    Boolean changed = null;
                    if (withStatus) {
                        try {
                            changed = !git.status(path).isClean();
                        } catch (GitException e) {
                            // unreadable (e.g. folder missing): leave the flag unknown
                        }
                    }
                    Integer unpushed = null;
                    Integer unpulled = null;
                    try {
                        unpushed = git.unpushedCount(path, MAX_UNPUSHED);
                        unpulled = git.unpulledCount(path, MAX_UNPUSHED);
                    } catch (GitException e) {
                        // unreadable: leave unknown
                    }
                    result.put(path, new Info(branch, changed, false, unpushed, unpulled));
                }
                return result;
            }

            @Override
            protected void done() {
                if (withStatus) {
                    fullRefreshRunning = false;
                }
                try {
                    apply(get(), started);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } catch (Exception e) {
                    // Branch and change markers are decoration only; the list stays usable without them.
                }
            }
        }.execute();
    }

    private void apply(Map<Path, Info> infos, long started) {
        for (int i = 0; i < model.size(); i++) {
            RepositoryEntry entry = model.get(i);
            Info info = infos.get(entry.path());
            if (info == null) {
                continue;
            }
            RepositoryEntry updated = entry.withMissing(info.missing());
            if (info.branch() != null) {
                updated = updated.withBranch(info.branch());
            }
            if (info.unpushed() != null) {
                updated = updated.withUnpushed(info.unpushed());
            }
            if (info.unpulled() != null) {
                updated = updated.withUnpulled(info.unpulled());
            }
            if (info.changed() != null && started >= changedStamps.getOrDefault(entry.path(), Long.MIN_VALUE)) {
                changedStamps.put(entry.path(), started);
                updated = updated.withChanged(info.changed());
            }
            if (!updated.equals(entry)) {
                model.set(i, updated);
            }
        }
    }
}
