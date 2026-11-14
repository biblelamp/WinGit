package cz.wingit.ui.repositories;

import java.awt.Color;
import java.awt.Component;
import java.awt.Font;
import java.nio.file.Path;
import java.util.function.Supplier;

import javax.swing.DefaultListCellRenderer;
import javax.swing.Icon;
import javax.swing.JList;
import javax.swing.UIManager;

import com.formdev.flatlaf.util.UIScale;

import cz.wingit.core.repo.RepositoryList;
import cz.wingit.ui.Messages;
import cz.wingit.ui.common.Icons;

/**
 * Renders an icon (red when the working tree has changes), "name (branch)" with the branch in a
 * dimmed color; the opened repository is bold; the full path is the tooltip.
 */
final class RepositoryCellRenderer extends DefaultListCellRenderer {

    private static final long serialVersionUID = 1L;

    private static final Icon CLEAN = Icons.repository(false);
    private static final Icon CHANGED = Icons.repository(true);

    private final transient Supplier<Path> opened;

    RepositoryCellRenderer(Supplier<Path> opened) {
        this.opened = opened;
    }

    @Override
    public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                                                  boolean isSelected, boolean cellHasFocus) {
        super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
        if (value instanceof RepositoryEntry entry) {
            Path openedPath = opened.get();
            boolean isOpened = openedPath != null && RepositoryList.samePath(openedPath, entry.path());
            setFont(list.getFont().deriveFont(isOpened ? Font.BOLD : Font.PLAIN));
            String name = escape(entry.displayName());
            if (entry.branch() == null) {
                setText(name);
            } else {
                Color dim = isSelected ? list.getSelectionForeground() : UIManager.getColor("Label.disabledForeground");
                setText("<html>" + name + " <span style='color:" + hex(dim) + "'>("
                        + escape(entry.branch()) + ")</span></html>");
            }
            boolean changed = Boolean.TRUE.equals(entry.changed());
            setIcon(changed ? CHANGED : CLEAN);
            setIconTextGap(UIScale.scale(6));
            setToolTipText(changed
                    ? Messages.format("repositories.tooltip.changed", entry.path())
                    : entry.path().toString());
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
