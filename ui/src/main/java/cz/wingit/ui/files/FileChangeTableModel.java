package cz.wingit.ui.files;

import java.io.File;
import java.util.List;

import javax.swing.table.AbstractTableModel;

import cz.wingit.core.status.ChangeType;
import cz.wingit.core.status.FileChange;
import cz.wingit.ui.Messages;

/**
 * Rows of the Files table: name, status text, folder (shown with Windows separators).
 */
final class FileChangeTableModel extends AbstractTableModel {

    private static final long serialVersionUID = 1L;

    static final int NAME = 0;
    static final int STATUS = 1;
    static final int FOLDER = 2;

    private static final String[] COLUMNS = {
            Messages.get("files.column.name"),
            Messages.get("files.column.status"),
            Messages.get("files.column.path"),
    };

    private transient List<FileChange> changes = List.of();

    void setChanges(List<FileChange> newChanges) {
        changes = List.copyOf(newChanges);
        fireTableDataChanged();
    }

    List<FileChange> changes() {
        return changes;
    }

    FileChange getChange(int row) {
        return changes.get(row);
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
        return String.class;
    }

    @Override
    public Object getValueAt(int row, int column) {
        FileChange change = changes.get(row);
        switch (column) {
            case NAME:
                return change.fileName();
            case STATUS:
                return statusText(change);
            case FOLDER:
                return change.directory().replace('/', File.separatorChar);
            default:
                throw new IllegalArgumentException("column " + column);
        }
    }

    /** E.g. "Modified", "Added (staged)", "Modified (staged), Deleted", "Conflict". */
    static String statusText(FileChange change) {
        if (change.staged() == null) {
            return typeText(change.unstaged());
        }
        String staged = Messages.format("files.status.staged", typeText(change.staged()));
        if (change.unstaged() == null) {
            return staged;
        }
        return staged + ", " + typeText(change.unstaged());
    }

    private static String typeText(ChangeType type) {
        switch (type) {
            case ADDED:
                return Messages.get("files.status.added");
            case MODIFIED:
                return Messages.get("files.status.modified");
            case DELETED:
                return Messages.get("files.status.deleted");
            case UNTRACKED:
                return Messages.get("files.status.untracked");
            case CONFLICTED:
                return Messages.get("files.status.conflicted");
            default:
                throw new IllegalArgumentException(String.valueOf(type));
        }
    }
}
