package cz.wingit.ui.branches;

import java.awt.Cursor;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutionException;

import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.JComponent;
import javax.swing.JPopupMenu;
import javax.swing.JTree;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreeNode;
import javax.swing.tree.TreePath;

import cz.wingit.core.GitService;
import cz.wingit.core.refs.LocalBranch;
import cz.wingit.core.refs.RemoteBranches;
import cz.wingit.core.refs.RepositoryRefs;
import cz.wingit.ui.Messages;
import cz.wingit.ui.common.Components;
import cz.wingit.ui.common.Dialogs;
import cz.wingit.ui.common.LatestLoader;
import cz.wingit.ui.common.PlaceholderTree;
import cz.wingit.ui.common.TitledPane;

/**
 * Branches, remotes, tags and stashes of the opened repository, read in the background.
 * Expanded groups stay expanded when the content is refreshed. Double-click or Enter on a branch
 * checks it out; on a remote branch it checks out the local branch of the same name, creating it
 * (tracking the remote one) if there is none.
 */
public final class BranchesPane extends TitledPane {

    private static final long serialVersionUID = 1L;
    private static final Set<String> DEFAULT_EXPANDED = Set.of(BranchTreeBuilder.LOCAL);

    private final transient GitService git;
    private final PlaceholderTree tree;
    private final transient LatestLoader<RepositoryRefs> loader;
    private transient RepositoryRefs shown;
    private transient Path workTree;
    private boolean switching;

    public BranchesPane(GitService git) {
        this(git, new PlaceholderTree(emptyModel()));
    }

    private BranchesPane(GitService git, PlaceholderTree tree) {
        super(Messages.get("branches.title"), Components.borderlessScroll(tree));
        this.git = git;
        this.tree = tree;
        this.loader = new LatestLoader<>(this::show, this::showError);
        tree.setRootVisible(false);
        tree.setShowsRootHandles(true);
        tree.setCellRenderer(new BranchCellRenderer());
        installSwitchGestures();
        showRepository(Optional.empty());
    }

    /** Shows the refs of {@code workTree}, or a hint when no repository is open. Call on the EDT. */
    public void showRepository(Optional<Path> workTree) {
        shown = null;
        this.workTree = workTree.orElse(null);
        tree.setModel(emptyModel());
        if (workTree.isEmpty()) {
            loader.cancel();
            tree.setPlaceholder(Messages.get("branches.placeholder.noRepository"));
            return;
        }
        tree.setPlaceholder(Messages.get("branches.placeholder.loading"));
        Path path = workTree.get();
        loader.load(() -> git.refs(path));
    }

    /** Re-reads the refs of the shown repository without clearing the tree first. Call on the EDT. */
    public void refresh() {
        loader.refresh();
    }

    /**
     * Checks out the branch of the selected node, if it has one (same as double-click / Enter).
     * Ignored while a previous switch is still running. Call on the EDT.
     */
    void switchToSelected() {
        TreePath selection = tree.getSelectionPath();
        if (selection == null || switching || workTree == null || shown == null) {
            return;
        }
        Object user = ((DefaultMutableTreeNode) selection.getLastPathComponent()).getUserObject();
        if (user instanceof BranchItem item && item.target() != null) {
            switchTo(workTree, item.target(), shown);
        }
    }

    private void switchTo(Path repo, BranchItem.Target target, RepositoryRefs refs) {
        boolean createLocal = target.isRemote() && refs.localBranches().stream()
                .noneMatch(branch -> branch.name().equals(target.branch()));
        if (target.isRemote() && !createLocal && refs.localBranches().stream()
                .anyMatch(branch -> branch.current() && branch.name().equals(target.branch()))) {
            return; // the local branch of that name is already checked out
        }
        switching = true;
        setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
        new SwingWorker<Void, Void>() {
            @Override
            protected Void doInBackground() {
                if (createLocal) {
                    git.switchToNewTrackingBranch(repo, target.remote(), target.branch());
                } else {
                    git.switchBranch(repo, target.branch());
                }
                return null;
            }

            @Override
            protected void done() {
                switching = false;
                setCursor(null);
                try {
                    get();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } catch (ExecutionException e) {
                    Dialogs.showError(SwingUtilities.getWindowAncestor(BranchesPane.this),
                            Messages.format("branches.switch.error.title", target.branch()), e.getCause());
                }
                // The file watcher refreshes the other panes; this one updates right away.
                refresh();
            }
        }.execute();
    }

    private void installSwitchGestures() {
        tree.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2 && SwingUtilities.isLeftMouseButton(e)
                        && tree.getPathForLocation(e.getX(), e.getY()) != null) {
                    switchToSelected();
                }
            }
        });
        tree.getInputMap(JComponent.WHEN_FOCUSED).put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), "switchBranch");
        tree.getActionMap().put("switchBranch", new AbstractAction() {
            private static final long serialVersionUID = 1L;

            @Override
            public void actionPerformed(ActionEvent e) {
                switchToSelected();
            }
        });
    }

    /** The open repository, if any. */
    public Optional<Path> repository() {
        return Optional.ofNullable(workTree);
    }

    /** The selected remote-tracking branch (remote and branch name), if one is selected. */
    Optional<BranchItem.Target> selectedRemoteBranch() {
        TreePath selection = tree.getSelectionPath();
        if (selection == null) {
            return Optional.empty();
        }
        Object user = ((DefaultMutableTreeNode) selection.getLastPathComponent()).getUserObject();
        if (user instanceof BranchItem item && item.target() != null && item.target().isRemote()) {
            return Optional.of(item.target());
        }
        return Optional.empty();
    }

    /**
     * The remote branch that local branch {@code branch} tracks, if it still exists on the remote
     * (as last fetched).
     */
    Optional<BranchItem.Target> upstreamOf(String branch) {
        if (shown == null) {
            return Optional.empty();
        }
        String upstream = shown.localBranches().stream().filter(b -> b.name().equals(branch))
                .map(LocalBranch::upstream).filter(Objects::nonNull).findFirst().orElse(null);
        if (upstream == null) {
            return Optional.empty();
        }
        for (RemoteBranches remote : shown.remotes()) {
            String prefix = remote.remote() + "/";
            if (upstream.startsWith(prefix) && remote.branches().contains(upstream.substring(prefix.length()))) {
                return Optional.of(new BranchItem.Target(remote.remote(), upstream.substring(prefix.length())));
            }
        }
        return Optional.empty();
    }

    /** Name of the selected local branch, also when it is the checked-out one. */
    Optional<String> selectedAnyLocalBranch() {
        TreePath selection = tree.getSelectionPath();
        if (selection == null) {
            return Optional.empty();
        }
        Object user = ((DefaultMutableTreeNode) selection.getLastPathComponent()).getUserObject();
        if (user instanceof BranchItem item && item.key().startsWith(BranchTreeBuilder.LOCAL_BRANCH_KEY)) {
            return Optional.of(item.key().substring(BranchTreeBuilder.LOCAL_BRANCH_KEY.length()));
        }
        return Optional.empty();
    }

    /** Branch names of {@code remote} as last fetched, empty if unknown. */
    List<String> remoteBranchNames(String remote) {
        if (shown == null) {
            return List.of();
        }
        return shown.remotes().stream().filter(r -> r.remote().equals(remote))
                .flatMap(r -> r.branches().stream()).toList();
    }

    /** Names of all local branches shown, empty while nothing is loaded. */
    List<String> localBranchNames() {
        return shown == null ? List.of() : shown.localBranches().stream().map(LocalBranch::name).toList();
    }

    /** Name of the selected local branch unless it is checked out (it cannot be deleted then). */
    public Optional<String> selectedLocalBranch() {
        TreePath selection = tree.getSelectionPath();
        if (selection == null) {
            return Optional.empty();
        }
        Object user = ((DefaultMutableTreeNode) selection.getLastPathComponent()).getUserObject();
        if (user instanceof BranchItem item && item.target() != null && !item.target().isRemote() && !item.current()) {
            return Optional.of(item.target().branch());
        }
        return Optional.empty();
    }

    /** Notified (on the EDT) when the selected node changes, also when the tree is reloaded. */
    public void addSelectionListener(Runnable listener) {
        tree.addTreeSelectionListener(e -> listener.run());
        tree.addPropertyChangeListener(JTree.TREE_MODEL_PROPERTY, e -> listener.run());
    }

    /**
     * Context menu with {@code actions} (right-click selects the node first); F2 runs {@code rename},
     * the Delete key runs {@code delete}.
     */
    public void installContextActions(Action rename, Action delete, Action... actions) {
        tree.getInputMap(JComponent.WHEN_FOCUSED).put(KeyStroke.getKeyStroke(KeyEvent.VK_DELETE, 0), "deleteBranch");
        tree.getActionMap().put("deleteBranch", delete);
        tree.getInputMap(JComponent.WHEN_FOCUSED).put(KeyStroke.getKeyStroke(KeyEvent.VK_F2, 0), "renameBranch");
        tree.getActionMap().put("renameBranch", rename);
        JPopupMenu popup = new JPopupMenu();
        for (Action action : actions) {
            popup.add(action);
        }
        popup.add(rename);
        popup.add(delete);
        tree.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                maybeShowPopup(e);
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                maybeShowPopup(e);
            }

            private void maybeShowPopup(MouseEvent e) {
                if (!e.isPopupTrigger() || workTree == null) {
                    return;
                }
                TreePath path = tree.getPathForLocation(e.getX(), e.getY());
                if (path != null) {
                    tree.setSelectionPath(path);
                }
                popup.show(tree, e.getX(), e.getY());
            }
        });
    }

    /** Selects the visible row whose label is {@code label} (for tests). */
    void selectRowForTest(String label) {
        for (int i = 0; i < tree.getRowCount(); i++) {
            Object user = ((DefaultMutableTreeNode) tree.getPathForRow(i).getLastPathComponent()).getUserObject();
            if (user instanceof BranchItem item && item.label().equals(label)) {
                tree.setSelectionRow(i);
                return;
            }
        }
        throw new IllegalArgumentException("No row " + label);
    }

    /** Labels of the visible rows (for tests). */
    List<String> visibleRows() {
        List<String> rows = new ArrayList<>();
        for (int i = 0; i < tree.getRowCount(); i++) {
            rows.add(String.valueOf(((DefaultMutableTreeNode) tree.getPathForRow(i).getLastPathComponent()).getUserObject()));
        }
        return rows;
    }

    /** Expands a visible row (for tests). */
    void expandRowForTest(int row) {
        tree.expandRow(row);
    }

    private void show(RepositoryRefs refs) {
        if (refs.equals(shown)) {
            return;
        }
        Set<String> expanded = shown == null ? DEFAULT_EXPANDED : expandedKeys();
        shown = refs;
        DefaultMutableTreeNode root = BranchTreeBuilder.build(refs);
        tree.setModel(new DefaultTreeModel(root));
        expand(root, expanded);
    }

    private void showError(Throwable error) {
        shown = null;
        tree.setModel(emptyModel());
        tree.setPlaceholder(Messages.format("branches.placeholder.error", error.getMessage()));
    }

    private Set<String> expandedKeys() {
        Set<String> keys = new HashSet<>();
        Object root = tree.getModel().getRoot();
        Enumeration<TreePath> paths = tree.getExpandedDescendants(new TreePath(root));
        for (TreePath path : paths == null ? Collections.<TreePath>emptyList() : Collections.list(paths)) {
            Object user = ((DefaultMutableTreeNode) path.getLastPathComponent()).getUserObject();
            if (user instanceof BranchItem item) {
                keys.add(item.key());
            }
        }
        return keys;
    }

    /** Expands nodes whose key is in {@code keys}, top-down, so collapsed parents stay collapsed. */
    private void expand(DefaultMutableTreeNode root, Set<String> keys) {
        Enumeration<TreeNode> nodes = root.breadthFirstEnumeration();
        while (nodes.hasMoreElements()) {
            DefaultMutableTreeNode node = (DefaultMutableTreeNode) nodes.nextElement();
            if (!(node.getUserObject() instanceof BranchItem item) || !keys.contains(item.key())) {
                continue;
            }
            DefaultMutableTreeNode parent = (DefaultMutableTreeNode) node.getParent();
            if (parent == root || tree.isExpanded(new TreePath(parent.getPath()))) {
                tree.expandPath(new TreePath(node.getPath()));
            }
        }
    }

    private static DefaultTreeModel emptyModel() {
        return new DefaultTreeModel(new DefaultMutableTreeNode());
    }
}
