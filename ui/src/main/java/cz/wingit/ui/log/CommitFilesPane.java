package cz.wingit.ui.log;

import java.awt.Component;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

import javax.swing.AbstractAction;
import javax.swing.Icon;
import javax.swing.JComponent;
import javax.swing.JTable;
import javax.swing.KeyStroke;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;

import com.formdev.flatlaf.util.UIScale;

import cz.wingit.core.log.CommitFile;
import cz.wingit.core.status.ChangeType;
import cz.wingit.ui.Messages;
import cz.wingit.ui.common.Components;
import cz.wingit.ui.common.Icons;
import cz.wingit.ui.common.PlaceholderTable;
import cz.wingit.ui.common.TitledPane;
import cz.wingit.ui.files.FileChangeTableModel;

/** Files changed by the selected commit: name with a status icon, change, folder. One file can be selected. */
final class CommitFilesPane extends TitledPane {

    private static final long serialVersionUID = 1L;

    private final FilesModel model;
    private final PlaceholderTable table;

    CommitFilesPane() {
        this(new FilesModel());
    }

    private CommitFilesPane(FilesModel model) {
        this(model, new PlaceholderTable(model));
    }

    private CommitFilesPane(FilesModel model, PlaceholderTable table) {
        super(Messages.get("files.title"), Components.borderlessScroll(table));
        this.model = model;
        this.table = table;
        table.setShowGrid(false);
        table.getTableHeader().setReorderingAllowed(false);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.getColumnModel().getColumn(FilesModel.NAME).setPreferredWidth(UIScale.scale(220));
        table.getColumnModel().getColumn(FilesModel.STATUS).setPreferredWidth(UIScale.scale(90));
        table.getColumnModel().getColumn(FilesModel.FOLDER).setPreferredWidth(UIScale.scale(220));
        table.getColumnModel().getColumn(FilesModel.NAME).setCellRenderer(new NameRenderer(model));
        showMessage(Messages.get("log.files.placeholder"));
    }

    /** No files, with a dimmed hint (none selected, loading, error). Call on the EDT. */
    void showMessage(String message) {
        model.setFiles(List.of());
        setTitle(Messages.get("files.title"));
        table.setPlaceholder(message);
    }

    /** Shows the files of a commit; the first one is selected. Call on the EDT. */
    void showFiles(List<CommitFile> files) {
        model.setFiles(files);
        setTitle(Messages.format("log.files.count", files.size()));
        table.setPlaceholder(Messages.get("log.files.empty"));
        if (!files.isEmpty()) {
            table.setRowSelectionInterval(0, 0);
        }
    }

    /** Notified (on the EDT) with the selected file whenever the selection changes, empty for none. */
    void addSelectionListener(Consumer<Optional<CommitFile>> listener) {
        table.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                listener.accept(selectedFile());
            }
        });
    }

    /**
     * Notified (on the EDT) when a file is double-clicked or Enter is pressed on the selected one, e.g. to
     * show its diff in a window of its own.
     */
    void addOpenListener(Consumer<CommitFile> listener) {
        // Enter opens the selected file (JTable would move to the next row)
        table.getInputMap(JComponent.WHEN_FOCUSED).put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), "openFile");
        table.getActionMap().put("openFile", new AbstractAction() {
            private static final long serialVersionUID = 1L;

            @Override
            public void actionPerformed(ActionEvent e) {
                selectedFile().ifPresent(listener);
            }
        });
        table.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() != 2 || !SwingUtilities.isLeftMouseButton(e)) {
                    return;
                }
                int row = table.rowAtPoint(e.getPoint());
                if (row >= 0 && row < table.getRowCount()) {
                    listener.accept(model.files.get(table.convertRowIndexToModel(row)));
                }
            }
        });
    }

    Optional<CommitFile> selectedFile() {
        int row = table.getSelectedRow();
        return row >= 0 && row < model.getRowCount()
                ? Optional.of(model.files.get(table.convertRowIndexToModel(row))) : Optional.empty();
    }

    /** Shown files (for tests). */
    List<CommitFile> files() {
        return model.files;
    }

    private static final class FilesModel extends AbstractTableModel {

        private static final long serialVersionUID = 1L;
        static final int NAME = 0;
        static final int STATUS = 1;
        static final int FOLDER = 2;
        private static final String[] COLUMNS = {
                Messages.get("files.column.name"),
                Messages.get("files.column.status"),
                Messages.get("files.column.path"),
        };

        private transient List<CommitFile> files = List.of();

        void setFiles(List<CommitFile> newFiles) {
            files = List.copyOf(newFiles);
            fireTableDataChanged();
        }

        @Override
        public int getRowCount() {
            return files.size();
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
            CommitFile file = files.get(row);
            switch (column) {
                case NAME:
                    return file.fileName();
                case STATUS:
                    return FileChangeTableModel.typeText(file.change());
                case FOLDER:
                default:
                    return file.directory().replace('/', File.separatorChar);
            }
        }
    }

    private static final class NameRenderer extends DefaultTableCellRenderer {

        private static final long serialVersionUID = 1L;
        private static final Map<ChangeType, Icon> ICONS = new EnumMap<>(ChangeType.class);
        private final FilesModel model;

        NameRenderer(FilesModel model) {
            this.model = model;
        }

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected,
                                                       boolean hasFocus, int row, int column) {
            super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
            CommitFile file = model.files.get(table.convertRowIndexToModel(row));
            setIcon(ICONS.computeIfAbsent(file.change(), Icons::fileStatus));
            setIconTextGap(UIScale.scale(6));
            setToolTipText(file.originalPath() == null ? null
                    : Messages.format("files.tooltip.renamed", file.originalPath().replace('/', File.separatorChar)));
            return this;
        }
    }
}
