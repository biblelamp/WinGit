package cz.wingit.ui.branches;

import javax.swing.JTree;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;

import cz.wingit.ui.Messages;
import cz.wingit.ui.common.Components;
import cz.wingit.ui.common.TitledPane;

/**
 * Tree of refs of the selected repository: local branches, remotes, tags, stashes.
 */
public final class BranchesPane extends TitledPane {

    private static final long serialVersionUID = 1L;

    public BranchesPane() {
        this(new JTree(createModel()));
    }

    private BranchesPane(JTree tree) {
        super(Messages.get("branches.title"), Components.borderlessScroll(tree));
        tree.setRootVisible(false);
        tree.setShowsRootHandles(true);
    }

    private static DefaultTreeModel createModel() {
        DefaultMutableTreeNode root = new DefaultMutableTreeNode();
        root.add(new DefaultMutableTreeNode(Messages.get("branches.local")));
        root.add(new DefaultMutableTreeNode(Messages.get("branches.remote")));
        root.add(new DefaultMutableTreeNode(Messages.get("branches.tags")));
        root.add(new DefaultMutableTreeNode(Messages.get("branches.stashes")));
        return new DefaultTreeModel(root);
    }
}
