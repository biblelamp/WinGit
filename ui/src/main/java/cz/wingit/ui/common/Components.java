package cz.wingit.ui.common;

import java.awt.Component;

import javax.swing.BorderFactory;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.table.DefaultTableModel;

/**
 * Small factory helpers shared by panes.
 */
public final class Components {

    private Components() {
    }

    /** Scroll pane without its own border; panes draw separators themselves. */
    public static JScrollPane borderlessScroll(Component view) {
        JScrollPane scroll = new JScrollPane(view);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        return scroll;
    }

    /** Read-only, row-selecting table with the given column titles and no rows. */
    public static JTable readOnlyTable(String... columns) {
        DefaultTableModel model = new DefaultTableModel(columns, 0) {
            private static final long serialVersionUID = 1L;

            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        JTable table = new JTable(model);
        table.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        table.setFillsViewportHeight(true);
        table.setShowGrid(false);
        table.getTableHeader().setReorderingAllowed(false);
        return table;
    }
}
