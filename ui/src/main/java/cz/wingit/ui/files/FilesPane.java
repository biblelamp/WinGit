package cz.wingit.ui.files;

import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.concurrent.ExecutionException;

import javax.swing.Action;
import javax.swing.JComponent;
import javax.swing.JPopupMenu;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.RowFilter;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.TableRowSorter;

import com.formdev.flatlaf.FlatClientProperties;
import com.formdev.flatlaf.util.UIScale;

import cz.wingit.core.GitService;
import cz.wingit.core.status.FileChange;
import cz.wingit.core.status.RepositoryStatus;
import cz.wingit.ui.Messages;
import cz.wingit.ui.common.Components;
import cz.wingit.ui.common.PlaceholderTable;
import cz.wingit.ui.common.TitledPane;

/**
 * Changed, untracked and conflicting files of the selected repository, with a quick filter
 * on the path. Status is read in the background; results of an outdated request are dropped.
 */
public final class FilesPane extends TitledPane {

    private static final long serialVersionUID = 1L;

    private final transient GitService git;
    private final FileChangeTableModel model;
    private final PlaceholderTable table;
    private final TableRowSorter<FileChangeTableModel> sorter;
    private final JTextField filter = new JTextField(20);
    private transient SwingWorker<RepositoryStatus, Void> loader;
    private transient Path current;
    private boolean refreshQueued;
    private final transient List<BiConsumer<Path, RepositoryStatus>> statusListeners = new ArrayList<>();
    private final transient List<Consumer<Optional<FileChange>>> fileSelectionListeners = new ArrayList<>();
    private final transient List<Consumer<FileChange>> fileOpenListeners = new ArrayList<>();
    private transient Optional<FileChange> notifiedSelection = Optional.empty();
    private boolean replacingRows;
    private int generation;
    /** File to select once the status of the shown repository has been read, or {@code null}. */
    private String pendingSelection;

    public FilesPane(GitService git) {
        this(git, new FileChangeTableModel());
    }

    private FilesPane(GitService git, FileChangeTableModel model) {
        this(git, model, new PlaceholderTable(model));
    }

    private FilesPane(GitService git, FileChangeTableModel model, PlaceholderTable table) {
        super(Messages.get("files.title"), Components.borderlessScroll(table));
        this.git = git;
        this.model = model;
        this.table = table;
        this.sorter = new TableRowSorter<>(model);
        configureTable();
        configureFilter();
        setHeaderTools(filter);
        showRepository(Optional.empty());
    }

    /** Shows the status of {@code workTree}, or a hint when nothing is selected. Call on the EDT. */
    public void showRepository(Optional<Path> workTree) {
        current = workTree.orElse(null);
        pendingSelection = null;
        model.setChanges(List.of());
        if (current == null) {
            cancelLoader();
            table.setPlaceholder(Messages.get("files.placeholder.noRepository"));
            return;
        }
        table.setPlaceholder(Messages.get("files.placeholder.loading"));
        load(current);
    }

    /**
     * Selects {@code path} (repository-relative, {@code /}-separated) when the status being read for the
     * shown repository arrives, e.g. the file selected in the last session. Nothing happens if the file has
     * no changes any more. Call on the EDT, after {@link #showRepository}.
     */
    public void selectWhenLoaded(String path) {
        pendingSelection = path;
    }

    /** The single selected file, if exactly one row is selected. Call on the EDT. */
    public Optional<FileChange> selectedFile() {
        List<FileChange> selected = selectedChanges();
        return selected.size() == 1 ? Optional.of(selected.get(0)) : Optional.empty();
    }

    /**
     * Notified (on the EDT) when the selected file changes: the file if exactly one row is selected,
     * otherwise empty. Not notified while rows are replaced by a refresh that keeps the selection.
     */
    public void addFileSelectionListener(Consumer<Optional<FileChange>> listener) {
        fileSelectionListeners.add(listener);
    }

    /** Notified (on the EDT) when a file is double-clicked, e.g. to show its diff in a window of its own. */
    public void addFileOpenListener(Consumer<FileChange> listener) {
        fileOpenListeners.add(listener);
    }

    /** Notified (on the EDT) with every status successfully read for the shown repository. */
    public void addStatusListener(BiConsumer<Path, RepositoryStatus> listener) {
        statusListeners.add(listener);
    }

    /**
     * Re-reads the status of the shown repository, keeping the current rows until the new result
     * arrives (no flicker) and keeping selected files selected. Call on the EDT.
     */
    public void refresh() {
        if (current == null) {
            return;
        }
        if (loader != null && !loader.isDone()) {
            refreshQueued = true; // don't restart a slow read; read once more when it finishes
            return;
        }
        load(current);
    }

    private void cancelLoader() {
        ++generation;
        refreshQueued = false;
        if (loader != null) {
            loader.cancel(false);
            loader = null;
        }
    }

    private void load(Path path) {
        cancelLoader();
        int request = generation;
        loader = new SwingWorker<>() {
            @Override
            protected RepositoryStatus doInBackground() {
                return git.status(path);
            }

            @Override
            protected void done() {
                if (request != generation || isCancelled()) {
                    return;
                }
                try {
                    RepositoryStatus status = get();
                    update(status.changes());
                    selectPending();
                    statusListeners.forEach(listener -> listener.accept(path, status));
                    table.setPlaceholder(Messages.get(status.isClean()
                            ? "files.placeholder.clean" : "files.placeholder.filtered"));
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } catch (ExecutionException e) {
                    Throwable cause = e.getCause() != null ? e.getCause() : e;
                    model.setChanges(List.of());
                    table.setPlaceholder(Messages.format("files.placeholder.error", cause.getMessage()));
                }
                if (refreshQueued) {
                    refreshQueued = false;
                    load(path);
                }
            }
        };
        loader.execute();
    }

    private void update(List<FileChange> changes) {
        if (changes.equals(model.changes())) {
            return;
        }
        Set<String> selected = new HashSet<>();
        for (int row : table.getSelectedRows()) {
            selected.add(model.getChange(table.convertRowIndexToModel(row)).path());
        }
        replacingRows = true;
        try {
            model.setChanges(changes);
            for (int row = 0; row < table.getRowCount(); row++) {
                if (selected.contains(model.getChange(table.convertRowIndexToModel(row)).path())) {
                    table.addRowSelectionInterval(row, row);
                }
            }
        } finally {
            replacingRows = false;
        }
        notifySelection();
    }

    private void selectPending() {
        String path = pendingSelection;
        pendingSelection = null;
        if (path == null || !table.getSelectionModel().isSelectionEmpty()) {
            return; // the user already picked something while the status was being read
        }
        for (int row = 0; row < table.getRowCount(); row++) {
            if (model.getChange(table.convertRowIndexToModel(row)).path().equals(path)) {
                table.setRowSelectionInterval(row, row);
                table.scrollRectToVisible(table.getCellRect(row, 0, true));
                return;
            }
        }
    }

    private void notifySelection() {
        int[] rows = table.getSelectedRows();
        Optional<FileChange> selection = rows.length == 1
                ? Optional.of(model.getChange(table.convertRowIndexToModel(rows[0])))
                : Optional.empty();
        if (!selection.equals(notifiedSelection)) {
            notifiedSelection = selection;
            fileSelectionListeners.forEach(listener -> listener.accept(selection));
        }
    }

    /** The repository shown, if any. */
    public Optional<Path> repository() {
        return Optional.ofNullable(current);
    }

    /** Notified (on the EDT) whenever the selected rows or their files change. */
    public void addSelectionChangeListener(Runnable listener) {
        table.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                listener.run();
            }
        });
        // Later, not now: model listeners run before the table has caught up with the new rows,
        // so reading the selection here would use rows that no longer exist.
        model.addTableModelListener(e -> SwingUtilities.invokeLater(listener));
    }

    /**
     * Context menu with {@code actions} ({@code null} = separator); a right-click on an unselected row
     * selects it first. The Delete key runs {@code delete}.
     */
    public void installContextActions(Action delete, Action... actions) {
        table.getInputMap(JComponent.WHEN_FOCUSED).put(KeyStroke.getKeyStroke(KeyEvent.VK_DELETE, 0), "deleteFiles");
        table.getActionMap().put("deleteFiles", delete);
        JPopupMenu popup = new JPopupMenu();
        for (Action action : actions) {
            if (action == null) {
                popup.addSeparator();
            } else {
                popup.add(action);
            }
        }
        table.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                maybeShowPopup(e);
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                maybeShowPopup(e);
            }

            private void maybeShowPopup(MouseEvent e) {
                if (!e.isPopupTrigger()) {
                    return;
                }
                int row = table.rowAtPoint(e.getPoint());
                if (row < 0) {
                    return;
                }
                if (!table.isRowSelected(row)) {
                    table.setRowSelectionInterval(row, row);
                }
                popup.show(table, e.getX(), e.getY());
            }
        });
    }

    /** Files of the selected rows, in the order shown. Call on the EDT. */
    public List<FileChange> selectedChanges() {
        List<FileChange> selected = new ArrayList<>();
        for (int row : table.getSelectedRows()) {
            int modelRow = row < table.getRowCount() ? table.convertRowIndexToModel(row) : -1;
            if (modelRow >= 0 && modelRow < model.getRowCount()) {
                selected.add(model.getChange(modelRow));
            }
        }
        return selected;
    }

    /** Selects all visible rows (for tests). */
    void selectAllRowsForTest() {
        table.selectAll();
    }

    /** Selects a visible row (for tests). */
    void selectRowForTest(int row) {
        table.setRowSelectionInterval(row, row);
    }

    /** Opens a visible row as a double-click does (for tests). */
    void openRowForTest(int row) {
        openRow(row);
    }

    /** Number of rows currently shown (after filtering). */
    int visibleRowCount() {
        return table.getRowCount();
    }

    private void openRow(int row) {
        int modelRow = row >= 0 && row < table.getRowCount() ? table.convertRowIndexToModel(row) : -1;
        if (modelRow >= 0 && modelRow < model.getRowCount()) {
            FileChange change = model.getChange(modelRow);
            fileOpenListeners.forEach(listener -> listener.accept(change));
        }
    }

    private void configureTable() {
        table.setShowGrid(false);
        table.getTableHeader().setReorderingAllowed(false);
        table.setRowSorter(sorter);
        table.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting() && !replacingRows) {
                notifySelection();
            }
        });
        table.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (SwingUtilities.isLeftMouseButton(e) && e.getClickCount() == 2) {
                    openRow(table.rowAtPoint(e.getPoint()));
                }
            }
        });
        table.getColumnModel().getColumn(FileChangeTableModel.NAME).setCellRenderer(new FileNameCellRenderer(model));
        table.getColumnModel().getColumn(FileChangeTableModel.NAME).setPreferredWidth(UIScale.scale(300));
        table.getColumnModel().getColumn(FileChangeTableModel.STATUS).setPreferredWidth(UIScale.scale(160));
        table.getColumnModel().getColumn(FileChangeTableModel.FOLDER).setPreferredWidth(UIScale.scale(400));
    }

    private void configureFilter() {
        filter.putClientProperty(FlatClientProperties.PLACEHOLDER_TEXT, Messages.get("files.filter.placeholder"));
        filter.putClientProperty(FlatClientProperties.TEXT_FIELD_SHOW_CLEAR_BUTTON, true);
        filter.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                applyFilter();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                applyFilter();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                applyFilter();
            }
        });
    }

    /** Case-insensitive substring match on the relative path; both / and \ work as separators. */
    void applyFilter() {
        String text = filter.getText().strip().toLowerCase(Locale.ROOT).replace('\\', '/');
        if (text.isEmpty()) {
            sorter.setRowFilter(null);
            return;
        }
        sorter.setRowFilter(new RowFilter<>() {
            @Override
            public boolean include(Entry<? extends FileChangeTableModel, ? extends Integer> entry) {
                return entry.getModel().getChange(entry.getIdentifier()).path()
                        .toLowerCase(Locale.ROOT).contains(text);
            }
        });
    }

    /** For tests: sets the filter text. */
    void setFilterText(String text) {
        filter.setText(text);
    }
}
