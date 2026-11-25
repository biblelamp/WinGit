package cz.wingit.ui.history;

import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.JComponent;
import javax.swing.JPopupMenu;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.SwingUtilities;

import com.formdev.flatlaf.FlatClientProperties;
import com.formdev.flatlaf.util.UIScale;

import cz.wingit.core.GitService;
import cz.wingit.core.log.CommitInfo;
import cz.wingit.ui.Messages;
import cz.wingit.ui.common.Components;
import cz.wingit.ui.common.LatestLoader;
import cz.wingit.ui.common.PlaceholderTable;
import cz.wingit.ui.common.TitledPane;

/**
 * Commits of the opened repository's HEAD with the commit graph and branch/tag labels. In the main
 * window only the last {@link #LIMIT} commits are read, so opening even a very large repository stays
 * fast; a {@link #fullHistory full-history} pane reads all of them in portions and shows them as they come.
 */
public final class HistoryPane extends TitledPane {

    private static final long serialVersionUID = 1L;

    /** How many commits are shown. */
    public static final int LIMIT = 50;

    private final transient GitService git;
    private final CommitTableModel model;
    private final PlaceholderTable table;
    private final transient LatestLoader<HistoryData> loader;
    /** Reads the whole history in a {@link #fullHistory} pane; {@code null} in a limited one. */
    private final transient HistoryStreamLoader stream;
    /** The whole history has been read (always true in a limited pane once loaded). */
    private boolean complete;
    /**
     * True while the rows are replaced: the table drops its selection and gets it back, which the selection
     * listeners should not see as "nothing selected" (e.g. the log window would clear the commit's details).
     */
    private boolean replacing;
    private final transient List<Consumer<Optional<CommitInfo>>> selectionListeners = new ArrayList<>();
    /** Quick filter in the header of a full-history pane; {@code null} in a limited one. */
    private final JTextField filter;
    /** Commit to select once it is loaded ({@link #selectCommit}); {@code null} if none. */
    private String pendingSelection;
    /** Title keys while the count is unknown and with the number of shown commits; {@code null} = fixed title. */
    private String titleKey;
    private String countedTitleKey;
    private String loadingTitleKey;
    /** {@code {1}} of the title keys, e.g. the shown branch; may be {@code null} if the keys do not use it. */
    private String titleArgument;
    /** Shown repository; {@code null} if none. */
    private transient Path repository;

    /** The last {@link #LIMIT} commits, as in the main window. */
    public HistoryPane(GitService git) {
        this(git, false);
    }

    /** The whole history, read in portions and shown as it comes. */
    public static HistoryPane fullHistory(GitService git) {
        return new HistoryPane(git, true);
    }

    private HistoryPane(GitService git, boolean full) {
        this(git, new CommitTableModel(CommitDates.system()), full);
    }

    private HistoryPane(GitService git, CommitTableModel model, boolean full) {
        this(git, model, new PlaceholderTable(model), full);
    }

    private HistoryPane(GitService git, CommitTableModel model, PlaceholderTable table, boolean full) {
        super(Messages.get("history.title"), Components.borderlessScroll(table));
        this.git = git;
        this.model = model;
        this.table = table;
        this.loader = new LatestLoader<>(this::show, this::showError);
        this.stream = full ? new HistoryStreamLoader(this::showPortion, this::showError) : null;
        this.filter = full ? createFilter() : null;
        if (filter != null) {
            setHeaderTools(filter);
        }
        table.setShowGrid(false);
        table.getTableHeader().setReorderingAllowed(false);
        table.getColumnModel().getColumn(CommitTableModel.MESSAGE).setPreferredWidth(UIScale.scale(600));
        table.getColumnModel().getColumn(CommitTableModel.MESSAGE).setCellRenderer(new GraphMessageRenderer(model));
        table.setRowHeight(Math.max(table.getRowHeight(), UIScale.scale(22)));
        table.setIntercellSpacing(new Dimension(0, 0));
        table.getColumnModel().getColumn(CommitTableModel.AUTHOR).setPreferredWidth(UIScale.scale(180));
        table.getColumnModel().getColumn(CommitTableModel.DATE).setPreferredWidth(UIScale.scale(160));
        CommitTextRenderer textRenderer = new CommitTextRenderer(model);
        table.getColumnModel().getColumn(CommitTableModel.AUTHOR).setCellRenderer(textRenderer);
        table.getColumnModel().getColumn(CommitTableModel.DATE).setCellRenderer(textRenderer);
        showRepository(Optional.empty());
    }

    /**
     * Shows the newest commits of {@code workTree} (a full-history pane: all of HEAD), or a hint when no
     * repository is open. Call on the EDT.
     */
    public void showRepository(Optional<Path> workTree) {
        repository = workTree.orElse(null);
        reload();
    }

    private void reload() {
        Optional<Path> workTree = Optional.ofNullable(repository);
        pendingSelection = null;
        complete = false;
        model.setData(HistoryData.EMPTY);
        loader.cancel();
        if (stream != null) {
            stream.cancel();
        }
        updateTitle();
        if (workTree.isEmpty()) {
            table.setPlaceholder(Messages.get("history.placeholder.noRepository"));
            return;
        }
        table.setPlaceholder(Messages.get("history.placeholder.loading"));
        Path path = workTree.get();
        if (stream != null) {
            stream.load(() -> git.openHistory(path, "HEAD"), () -> git.historyVersion(path));
        } else {
            loader.load(() -> HistoryData.of(git.recentCommits(path, LIMIT)));
        }
    }

    /** Re-reads the commits without clearing the table first. Call on the EDT. */
    public void refresh() {
        if (stream != null) {
            stream.refresh();
        } else {
            loader.refresh();
        }
    }

    /** The selected commit, if a row is selected. */
    public Optional<CommitInfo> selectedCommit() {
        int row = table.getSelectedRow();
        return row >= 0 && row < model.getRowCount() ? Optional.of(model.getCommit(row)) : Optional.empty();
    }

    /**
     * Notified (on the EDT) with the selected commit whenever the selection changes, empty when none
     * is selected. Re-reading the history keeps the selection; the listener may then get the same commit again.
     */
    public void addSelectionListener(Consumer<Optional<CommitInfo>> listener) {
        selectionListeners.add(listener);
        table.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting() && !replacing) {
                listener.accept(selectedCommit());
            }
        });
    }

    /**
     * Uses {@code titleKey} as the title while no commits are shown, {@code loadingTitleKey} while more are
     * being read and {@code countedTitleKey} once all are; the last two get the number of shown commits as
     * {@code {0}}, e.g. "Commits (12,500\u2026)" and "Commits (50)". All three get {@code argument} as
     * {@code {1}} (e.g. the branch shown). Call on the EDT, again whenever the title should change.
     */
    public void useCountedTitle(String titleKey, String loadingTitleKey, String countedTitleKey, String argument) {
        this.titleKey = titleKey;
        this.loadingTitleKey = loadingTitleKey;
        this.countedTitleKey = countedTitleKey;
        this.titleArgument = argument;
        updateTitle();
    }

    private void updateTitle() {
        if (titleKey == null) {
            return;
        }
        int count = model.totalCount();
        if (count == 0) {
            setTitle(Messages.format(titleKey, count, titleArgument));
        } else {
            setTitle(Messages.format(complete ? countedTitleKey : loadingTitleKey, count, titleArgument));
        }
    }

    /** True while a full-history pane is reading (also during a quiet refresh). */
    boolean isReading() {
        return stream != null && stream.isRunning();
    }

    /** True once the whole history (or, in a limited pane, the last commits) has been read. */
    public boolean isComplete() {
        return complete;
    }

    /** Notified (on the EDT) when the user double-clicks a commit row. */
    public void addOpenListener(Consumer<CommitInfo> listener) {
        table.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() != 2 || !SwingUtilities.isLeftMouseButton(e)) {
                    return;
                }
                int row = table.rowAtPoint(e.getPoint());
                if (row >= 0 && row < model.getRowCount()) {
                    listener.accept(model.getCommit(row));
                }
            }
        });
    }

    /**
     * Selects the commit {@code id} and scrolls to it; if the commits are still being read, selects it
     * once they are. Call on the EDT.
     */
    public void selectCommit(String id) {
        pendingSelection = select(id) ? null : id;
    }

    /** Context menu of commit rows with {@code actions}; right-click selects the row first. */
    public void installContextActions(Action... actions) {
        JPopupMenu popup = new JPopupMenu();
        for (Action action : actions) {
            popup.add(action);
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
                table.setRowSelectionInterval(row, row);
                popup.show(table, e.getX(), e.getY());
            }
        });
    }

    private JTextField createFilter() {
        JTextField field = new JTextField(20);
        field.putClientProperty(FlatClientProperties.PLACEHOLDER_TEXT, Messages.get("history.filter.placeholder"));
        field.putClientProperty(FlatClientProperties.TEXT_FIELD_SHOW_CLEAR_BUTTON, true);
        field.setToolTipText(Messages.get("history.filter.tooltip"));
        field.getInputMap(JComponent.WHEN_FOCUSED).put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "clearFilter");
        field.getActionMap().put("clearFilter", new AbstractAction() {
            private static final long serialVersionUID = 1L;

            /** Disabled for an empty filter: the key is not used here and goes on to the window (it closes). */
            @Override
            public boolean isEnabled() {
                return !field.getText().isEmpty();
            }

            @Override
            public void actionPerformed(ActionEvent e) {
                field.setText("");
            }
        });
        field.getDocument().addDocumentListener(new DocumentListener() {
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
        return field;
    }

    /**
     * Shows only the commits matching the filter text. The selected commit stays selected (and in view) if
     * it still matches; the selection listeners hear only about a real change.
     */
    private void applyFilter() {
        Optional<CommitInfo> before = selectedCommit();
        replacing = true;
        try {
            if (!model.setFilter(filter.getText())) {
                return;
            }
            before.ifPresent(commit -> select(commit.id()));
        } finally {
            replacing = false;
        }
        Optional<CommitInfo> after = selectedCommit();
        if (!after.map(CommitInfo::id).equals(before.map(CommitInfo::id))) {
            selectionListeners.forEach(listener -> listener.accept(after));
        }
        if (complete) {
            table.setPlaceholder(emptyPlaceholder());
        }
    }

    /** Hint for a table without rows once everything is read: no commits, or none matching the filter. */
    private String emptyPlaceholder() {
        return Messages.get(model.isFiltered() && model.totalCount() > 0
                ? "history.placeholder.filtered" : "history.placeholder.empty");
    }

    /** The filter field (for tests). */
    JTextField filterField() {
        return filter;
    }

    /** For tests: sets the filter text (full-history pane). */
    void setFilterText(String text) {
        filter.setText(text);
    }

    /** Messages of the rows the table shows, after filtering (for tests). */
    List<String> shownMessages() {
        List<String> shown = new ArrayList<>();
        for (int row = 0; row < model.getRowCount(); row++) {
            shown.add(model.getCommit(row).message());
        }
        return shown;
    }

    /** Selects a row (for tests). */
    void selectRowForTest(int row) {
        table.setRowSelectionInterval(row, row);
    }

    /** Messages of the shown rows (for tests). */
    List<String> messages() {
        return model.data().commits().stream().map(CommitInfo::message).toList();
    }

    private boolean select(String id) {
        return select(id, 0);
    }

    /** Selects and scrolls to commit {@code id}, looking at rows from {@code fromRow}. */
    private boolean select(String id, int fromRow) {
        for (int row = fromRow; row < model.getRowCount(); row++) {
            if (model.getCommit(row).id().equals(id)) {
                table.setRowSelectionInterval(row, row);
                table.scrollRectToVisible(table.getCellRect(row, 0, true));
                return true;
            }
        }
        return false;
    }

    private void show(HistoryData data) {
        complete = true;
        replace(data);
        pendingSelection = null; // not among the last commits: nothing to select
        table.setPlaceholder(emptyPlaceholder());
        updateTitle();
    }

    /** A portion of the whole history (full-history pane). */
    private void showPortion(HistoryStreamLoader.Portion portion) {
        int from = portion.replace() ? 0 : model.getRowCount();
        if (portion.replace()) {
            replace(portion.data());
        } else {
            model.append(portion.data());
        }
        if (pendingSelection != null && select(pendingSelection, from)) {
            pendingSelection = null;
        }
        complete = portion.complete();
        if (complete) {
            pendingSelection = null; // not in the history at all
            table.setPlaceholder(emptyPlaceholder());
        }
        updateTitle();
    }

    /** Shows {@code data} instead of the current rows, keeping the selected commit selected. */
    private void replace(HistoryData data) {
        if (pendingSelection != null) {
            model.setData(data);
            if (select(pendingSelection)) {
                pendingSelection = null;
            }
        } else if (!model.shows(data)) {
            Optional<CommitInfo> before = selectedCommit();
            replacing = true;
            try {
                model.setData(data);
                before.ifPresent(commit -> {
                    // select it again where it is now, without scrolling the view
                    for (int row = 0; row < model.getRowCount(); row++) {
                        if (model.getCommit(row).id().equals(commit.id())) {
                            table.setRowSelectionInterval(row, row);
                            break;
                        }
                    }
                });
            } finally {
                replacing = false;
            }
            Optional<CommitInfo> after = selectedCommit();
            if (!after.map(CommitInfo::id).equals(before.map(CommitInfo::id))) {
                selectionListeners.forEach(listener -> listener.accept(after));
            }
        }
    }

    private void showError(Throwable error) {
        complete = true;
        pendingSelection = null;
        model.setData(HistoryData.EMPTY);
        table.setPlaceholder(Messages.format("history.placeholder.error", error.getMessage()));
        updateTitle();
    }
}
