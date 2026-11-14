package cz.wingit.ui.branches;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreeNode;
import javax.swing.tree.TreePath;

import cz.wingit.core.GitService;
import cz.wingit.core.refs.RepositoryRefs;
import cz.wingit.ui.Messages;
import cz.wingit.ui.common.Components;
import cz.wingit.ui.common.LatestLoader;
import cz.wingit.ui.common.PlaceholderTree;
import cz.wingit.ui.common.TitledPane;

/**
 * Branches, remotes, tags and stashes of the opened repository, read in the background.
 * Expanded groups stay expanded when the content is refreshed.
 */
public final class BranchesPane extends TitledPane {

    private static final long serialVersionUID = 1L;
    private static final Set<String> DEFAULT_EXPANDED = Set.of(BranchTreeBuilder.LOCAL);

    private final transient GitService git;
    private final PlaceholderTree tree;
    private final transient LatestLoader<RepositoryRefs> loader;
    private transient RepositoryRefs shown;

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
        showRepository(Optional.empty());
    }

    /** Shows the refs of {@code workTree}, or a hint when no repository is open. Call on the EDT. */
    public void showRepository(Optional<Path> workTree) {
        shown = null;
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
