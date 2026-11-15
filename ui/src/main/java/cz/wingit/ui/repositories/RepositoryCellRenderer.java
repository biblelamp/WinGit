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
 * dimmed color; the opened repository is bold; the full path is the tooltip. A repository whose
 * folder is gone gets its own icon and a "missing" marker instead of the branch. Unpushed commits
 * of the current branch are shown as an arrow with their count after the branch.
 */
final class RepositoryCellRenderer extends DefaultListCellRenderer {

    private static final long serialVersionUID = 1L;

    private static final Icon CLEAN = Icons.repository(false);
    private static final Icon CHANGED = Icons.repository(true);
    private static final Icon MISSING = Icons.repositoryMissing();

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
            String detail = entry.missing() ? Messages.get("repositories.missing")
                    : entry.branch() != null ? "(" + entry.branch() + ")" : null;
            String outgoing = entry.needsPush() && !entry.missing()
                    // bold in the regular text color: a gray marker would vanish next to the dimmed branch
                    ? " <b>" + Messages.format("repositories.unpushed", unpushedText(entry.unpushed())) + "</b>"
                    : "";
            if (detail == null && outgoing.isEmpty()) {
                setText(name);
            } else {
                Color dim = isSelected ? list.getSelectionForeground() : UIManager.getColor("Label.disabledForeground");
                String detailHtml = detail == null ? ""
                        : " <span style='color:" + hex(dim) + "'>" + escape(detail) + "</span>";
                setText("<html>" + name + detailHtml + outgoing + "</html>");
            }
            boolean changed = Boolean.TRUE.equals(entry.changed());
            setIcon(entry.missing() ? MISSING : changed ? CHANGED : CLEAN);
            setIconTextGap(UIScale.scale(6));
            if (entry.missing()) {
                setToolTipText(Messages.format("repositories.tooltip.missing", entry.path()));
            } else {
                String tooltip = changed
                        ? Messages.format("repositories.tooltip.changed", entry.path())
                        : entry.path().toString();
                if (entry.needsPush()) {
                    tooltip += " " + Messages.format("repositories.tooltip.unpushed", unpushedText(entry.unpushed()));
                }
                setToolTipText(tooltip);
            }
        }
        return this;
    }

    private static String unpushedText(int count) {
        return count >= RepositoriesPane.MAX_UNPUSHED ? (RepositoriesPane.MAX_UNPUSHED - 1) + "+" : Integer.toString(count);
    }

    private static String hex(Color color) {
        Color c = color != null ? color : Color.GRAY;
        return String.format("#%02x%02x%02x", c.getRed(), c.getGreen(), c.getBlue());
    }

    private static String escape(String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
