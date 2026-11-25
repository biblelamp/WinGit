package cz.wingit.ui.history;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

import javax.swing.table.AbstractTableModel;

import cz.wingit.core.graph.GraphRow;
import cz.wingit.core.log.CommitInfo;
import cz.wingit.core.log.RefLabel;
import cz.wingit.ui.Messages;

/**
 * Rows of the History table: message (painted with graph and labels), author, date.
 * <p>
 * With a {@link #setFilter filter}, only the matching commits are rows. They are no longer neighbours in
 * the history, so their graph is just a node each, without lines.
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
    /** Shown commits and their graph rows (same order); appended to while a long history is read. */
    private transient List<CommitInfo> commits = new ArrayList<>();
    private transient List<GraphRow> graph = new ArrayList<>();
    private int maxLaneCount;
    /** Lower-case filter text; {@code null} = all commits are rows. */
    private String filter;
    /** With a filter: indexes into {@link #commits} of the rows, in order. */
    private final List<Integer> matches = new ArrayList<>();

    /** Graph of a row while filtering: the node only. */
    private static final GraphRow LONE_NODE = new GraphRow(0, List.of(), List.of(), 1);

    CommitTableModel(CommitDates dates) {
        this.dates = dates;
    }

    void setData(HistoryData newData) {
        commits = new ArrayList<>(newData.commits());
        graph = new ArrayList<>(newData.graph());
        maxLaneCount = newData.maxLaneCount();
        rebuildMatches();
        fireTableDataChanged();
    }

    /**
     * Shows only the commits whose message, author, branch or tag names contain {@code text}, or whose id
     * starts with it (case-insensitive); blank shows all. Returns whether the rows changed.
     */
    boolean setFilter(String text) {
        String normalized = text == null || text.isBlank() ? null : text.strip().toLowerCase(Locale.ROOT);
        if (Objects.equals(normalized, filter)) {
            return false;
        }
        filter = normalized;
        rebuildMatches();
        fireTableDataChanged();
        return true;
    }

    boolean isFiltered() {
        return filter != null;
    }

    /** Number of all commits, also those the filter hides. */
    int totalCount() {
        return commits.size();
    }

    private void rebuildMatches() {
        matches.clear();
        if (filter != null) {
            for (int i = 0; i < commits.size(); i++) {
                if (matches(commits.get(i), filter)) {
                    matches.add(i);
                }
            }
        }
    }

    static boolean matches(CommitInfo commit, String lowerCaseText) {
        if (commit.id().startsWith(lowerCaseText)
                || commit.message().toLowerCase(Locale.ROOT).contains(lowerCaseText)
                || commit.author().toLowerCase(Locale.ROOT).contains(lowerCaseText)) {
            return true;
        }
        for (RefLabel ref : commit.refs()) {
            if (ref.name().toLowerCase(Locale.ROOT).contains(lowerCaseText)) {
                return true;
            }
        }
        return false;
    }

    /** Adds older commits below the shown ones; selection and scroll position stay. */
    void append(HistoryData older) {
        if (older.commits().isEmpty()) {
            return;
        }
        int first = commits.size();
        commits.addAll(older.commits());
        graph.addAll(older.graph());
        maxLaneCount = Math.max(maxLaneCount, older.maxLaneCount());
        if (filter == null) {
            fireTableRowsInserted(first, commits.size() - 1);
            return;
        }
        int firstRow = matches.size();
        for (int i = first; i < commits.size(); i++) {
            if (matches(commits.get(i), filter)) {
                matches.add(i);
            }
        }
        if (matches.size() > firstRow) {
            fireTableRowsInserted(firstRow, matches.size() - 1);
        }
    }

    /** A copy of all commits (also those the filter hides). */
    HistoryData data() {
        return new HistoryData(commits, graph);
    }

    /** Whether all commits are exactly {@code other} (without copying them). */
    boolean shows(HistoryData other) {
        return commits.equals(other.commits()) && graph.equals(other.graph());
    }

    CommitInfo getCommit(int row) {
        return commits.get(index(row));
    }

    GraphRow getGraphRow(int row) {
        return filter == null ? graph.get(row) : LONE_NODE;
    }

    /** Widest graph of all rows, so messages line up. */
    int maxLaneCount() {
        return filter == null ? maxLaneCount : 1;
    }

    private int index(int row) {
        return filter == null ? row : matches.get(row);
    }

    @Override
    public int getRowCount() {
        return filter == null ? commits.size() : matches.size();
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
        CommitInfo commit = getCommit(row);
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
