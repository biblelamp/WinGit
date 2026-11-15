package cz.wingit.ui.commit;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import javax.swing.table.AbstractTableModel;

import cz.wingit.core.status.FileChange;
import cz.wingit.ui.Messages;
import cz.wingit.ui.files.FileChangeTableModel;

/**
 * Changed files in the commit dialog, each with a check box deciding whether it is committed.
 */
final class CommitFileTableModel extends AbstractTableModel {

    private static final long serialVersionUID = 1L;

    static final int INCLUDE = 0;
    static final int NAME = 1;
    static final int STATUS = 2;
    static final int FOLDER = 3;

    private static final String[] COLUMNS = {
            "",
            Messages.get("files.column.name"),
            Messages.get("files.column.status"),
            Messages.get("files.column.path"),
    };

    private final transient List<FileChange> changes;
    private final boolean[] included;

    /** All files are included, or only {@code preselected} ones if that is not empty. */
    CommitFileTableModel(List<FileChange> changes, Collection<FileChange> preselected) {
        this.changes = List.copyOf(changes);
        this.included = new boolean[changes.size()];
        for (int i = 0; i < included.length; i++) {
            included[i] = preselected.isEmpty() || preselected.contains(this.changes.get(i));
        }
    }

    List<FileChange> includedChanges() {
        List<FileChange> result = new ArrayList<>();
        for (int i = 0; i < included.length; i++) {
            if (included[i]) {
                result.add(changes.get(i));
            }
        }
        return result;
    }

    void setAllIncluded(boolean include) {
        Arrays.fill(included, include);
        fireTableRowsUpdated(0, Math.max(0, included.length - 1));
    }

    @Override
    public int getRowCount() {
        return changes.size();
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
    public Class<?> getColumnClass(int column) {
        return column == INCLUDE ? Boolean.class : String.class;
    }

    @Override
    public boolean isCellEditable(int row, int column) {
        return column == INCLUDE;
    }

    @Override
    public void setValueAt(Object value, int row, int column) {
        if (column == INCLUDE) {
            included[row] = Boolean.TRUE.equals(value);
            fireTableCellUpdated(row, column);
        }
    }

    @Override
    public Object getValueAt(int row, int column) {
        FileChange change = changes.get(row);
        switch (column) {
            case INCLUDE:
                return included[row];
            case NAME:
                return change.fileName();
            case STATUS:
                return FileChangeTableModel.statusText(change);
            case FOLDER:
                return change.directory().replace('/', File.separatorChar);
            default:
                throw new IllegalArgumentException("column " + column);
        }
    }
}
