package cz.wingit.ui.history;

import javax.swing.table.AbstractTableModel;

import cz.wingit.core.graph.GraphRow;
import cz.wingit.core.log.CommitInfo;
import cz.wingit.ui.Messages;

/**
 * Rows of the History table: message (painted with graph and labels), author, date.
 */
final class CommitTableModel extends AbstractTableModel {

    private static final long serialVersionUID = 1L;

    static final int MESSAGE = 0;
    static final int AUTHOR = 1;
    static final int DATE = 2;

    private static final String[] COLUMNS = {
            Messages.get("history.column.message"),
            Messages.get("history.column.author"),
            Messages.get("history.column.date"),
    };

    private final transient CommitDates dates;
    private transient HistoryData data = HistoryData.EMPTY;
    private int maxLaneCount;

    CommitTableModel(CommitDates dates) {
        this.dates = dates;
    }

    void setData(HistoryData newData) {
        data = newData;
        maxLaneCount = newData.maxLaneCount();
        fireTableDataChanged();
    }

    HistoryData data() {
        return data;
    }

    CommitInfo getCommit(int row) {
        return data.commits().get(row);
    }

    GraphRow getGraphRow(int row) {
        return data.graph().get(row);
    }

    /** Widest graph of all rows, so messages line up. */
    int maxLaneCount() {
        return maxLaneCount;
    }

    @Override
    public int getRowCount() {
        return data.commits().size();
    }

    @Override
    public int getColumnCount() {
        return COLUMNS.length;
    }

    @Override
    public String getColumnName(int column) {
        return COLUMNS[column];
    }

    @Override
    public Object getValueAt(int row, int column) {
        CommitInfo commit = data.commits().get(row);
        switch (column) {
            case MESSAGE:
                return commit.message();
            case AUTHOR:
                return commit.author();
            case DATE:
                return dates.format(commit.authorTime());
            default:
                throw new IllegalArgumentException("column " + column);
        }
    }
}
