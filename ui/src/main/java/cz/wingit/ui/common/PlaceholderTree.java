package cz.wingit.ui.common;

import java.awt.Graphics;

import javax.swing.JTree;
import javax.swing.tree.TreeModel;

/**
 * JTree that paints a dimmed hint while its (hidden) root has no children.
 */
public class PlaceholderTree extends JTree {

    private static final long serialVersionUID = 1L;

    private String placeholder = "";

    public PlaceholderTree(TreeModel model) {
        super(model);
    }

    public void setPlaceholder(String text) {
        placeholder = text;
        repaint();
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        TreeModel model = getModel();
        Object root = model == null ? null : model.getRoot();
        if (root == null || model.getChildCount(root) == 0) {
            Placeholder.paint(g, this, placeholder);
        }
    }
}
