package cz.wingit.ui.repositories;

import java.awt.Color;
import java.awt.Component;

import javax.swing.DefaultListCellRenderer;
import javax.swing.JList;
import javax.swing.UIManager;

/**
 * Renders "name (branch)" with the branch in a dimmed color; the full path is the tooltip.
 */
final class RepositoryCellRenderer extends DefaultListCellRenderer {

    private static final long serialVersionUID = 1L;

    @Override
    public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                                                  boolean isSelected, boolean cellHasFocus) {
        super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
        if (value instanceof RepositoryEntry entry) {
            String name = escape(entry.displayName());
            if (entry.branch() == null) {
                setText(name);
            } else {
                Color dim = isSelected ? list.getSelectionForeground() : UIManager.getColor("Label.disabledForeground");
                setText("<html>" + name + " <span style='color:" + hex(dim) + "'>("
                        + escape(entry.branch()) + ")</span></html>");
            }
            setToolTipText(entry.path().toString());
        }
        return this;
    }

    private static String hex(Color color) {
        Color c = color != null ? color : Color.GRAY;
        return String.format("#%02x%02x%02x", c.getRed(), c.getGreen(), c.getBlue());
    }

    private static String escape(String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
