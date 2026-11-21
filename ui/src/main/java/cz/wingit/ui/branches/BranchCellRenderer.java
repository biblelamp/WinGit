package cz.wingit.ui.branches;

import java.awt.Color;
import java.awt.Component;

import javax.swing.JTree;
import javax.swing.UIManager;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeCellRenderer;

/**
 * Label with an optional dimmed detail; the checked-out branch is bold. No folder/leaf icons.
 */
final class BranchCellRenderer extends DefaultTreeCellRenderer {

    private static final long serialVersionUID = 1L;

    @Override
    public Component getTreeCellRendererComponent(JTree tree, Object value, boolean selected, boolean expanded,
                                                  boolean leaf, int row, boolean hasFocus) {
        super.getTreeCellRendererComponent(tree, value, selected, expanded, leaf, row, hasFocus);
        setIcon(null);
        Object user = value instanceof DefaultMutableTreeNode node ? node.getUserObject() : null;
        if (user instanceof BranchItem item) {
            setText(html(item));
        }
        return this;
    }

    private static String html(BranchItem item) {
        String label = escape(item.label());
        if (item.current()) {
            label = "<b>" + label + "</b>";
        }
        if (item.detail() == null) {
            return "<html>" + label + "</html>";
        }
        Color dim = UIManager.getColor("Label.disabledForeground"); // readable on the light selection too
        return "<html>" + label + " <span style='color:" + hex(dim) + "'>" + escape(item.detail()) + "</span></html>";
    }

    private static String hex(Color color) {
        Color c = color != null ? color : Color.GRAY;
        return String.format("#%02x%02x%02x", c.getRed(), c.getGreen(), c.getBlue());
    }

    private static String escape(String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
