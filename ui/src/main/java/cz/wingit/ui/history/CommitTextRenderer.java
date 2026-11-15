package cz.wingit.ui.history;

import java.awt.Component;
import java.awt.Font;

import javax.swing.JTable;
import javax.swing.table.DefaultTableCellRenderer;

import cz.wingit.core.log.CommitInfo;
import cz.wingit.ui.Messages;

/**
 * Plain text cell of the History table (author, date); bold for commits that are not pushed yet,
 * like the message column.
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
        setToolTipText(commit.unpushed() ? Messages.get("history.unpushed") : null);
        return this;
    }

    /** Bold for unpushed commits, the table font otherwise. */
    static Font fontFor(CommitInfo commit, Font base) {
        return commit.unpushed() ? base.deriveFont(Font.BOLD) : base;
    }
}
