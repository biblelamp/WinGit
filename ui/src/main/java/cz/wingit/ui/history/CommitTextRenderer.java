package cz.wingit.ui.history;

import java.awt.Color;
import java.awt.Component;
import java.awt.Font;

import javax.swing.JTable;
import javax.swing.UIManager;
import javax.swing.table.DefaultTableCellRenderer;

import cz.wingit.core.log.CommitInfo;
import cz.wingit.ui.Messages;

/**
 * Plain text cell of the History table (author, date); bold for commits that are not pushed yet,
 * like the message column; dimmed for commits on the server that are not pulled yet.
 */
final class CommitTextRenderer extends DefaultTableCellRenderer {

    private static final long serialVersionUID = 1L;

    private final CommitTableModel model;

    CommitTextRenderer(CommitTableModel model) {
        this.model = model;
    }

    @Override
    public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected,
                                                   boolean hasFocus, int row, int column) {
        super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
        CommitInfo commit = model.getCommit(table.convertRowIndexToModel(row));
        setFont(fontFor(commit, table.getFont()));
        setForeground(foregroundFor(commit, table)); // the selection is light: dimmed rows stay dimmed
        setToolTipText(commit.unpushed() ? Messages.get("history.unpushed")
                : commit.unpulled() ? Messages.get("history.unpulled") : null);
        return this;
    }

    /**
     * Text color of an unselected row. Always set: the renderer would otherwise keep a dimmed color
     * for the following rows.
     */
    static Color foregroundFor(CommitInfo commit, JTable table) {
        return commit.unpulled() ? unpulledForeground() : table.getForeground();
    }

    /** Dimmed text of commits that are on the server but not pulled yet ("not here yet"). */
    static Color unpulledForeground() {
        Color color = UIManager.getColor("Label.disabledForeground");
        return color != null ? color : Color.GRAY;
    }

    /** Bold for unpushed commits, the table font otherwise. */
    static Font fontFor(CommitInfo commit, Font base) {
        return commit.unpushed() ? base.deriveFont(Font.BOLD) : base;
    }
}
