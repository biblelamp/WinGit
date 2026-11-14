package cz.wingit.ui.history;

import javax.swing.JTable;

import com.formdev.flatlaf.util.UIScale;

import cz.wingit.ui.Messages;
import cz.wingit.ui.common.Components;
import cz.wingit.ui.common.TitledPane;

/**
 * Recent commits of the current branch. The graph column and virtualized model come later.
 */
public final class HistoryPane extends TitledPane {

    private static final long serialVersionUID = 1L;

    public HistoryPane() {
        this(Components.readOnlyTable(
                Messages.get("history.column.message"),
                Messages.get("history.column.author"),
                Messages.get("history.column.date")));
    }

    private HistoryPane(JTable table) {
        super(Messages.get("history.title"), Components.borderlessScroll(table));
        table.getColumnModel().getColumn(0).setPreferredWidth(UIScale.scale(600));
        table.getColumnModel().getColumn(1).setPreferredWidth(UIScale.scale(180));
        table.getColumnModel().getColumn(2).setPreferredWidth(UIScale.scale(160));
    }
}
