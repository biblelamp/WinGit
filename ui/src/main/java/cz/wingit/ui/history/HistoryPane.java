package cz.wingit.ui.history;

import java.awt.Dimension;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import com.formdev.flatlaf.util.UIScale;

import cz.wingit.core.GitService;
import cz.wingit.core.log.CommitInfo;
import cz.wingit.ui.Messages;
import cz.wingit.ui.common.Components;
import cz.wingit.ui.common.LatestLoader;
import cz.wingit.ui.common.PlaceholderTable;
import cz.wingit.ui.common.TitledPane;

/**
 * The newest commits of the opened repository's HEAD with the commit graph and branch/tag labels.
 * Only the last {@link #LIMIT} commits are read, so opening even a very large repository stays fast.
 */
public final class HistoryPane extends TitledPane {

    private static final long serialVersionUID = 1L;

    /** How many commits are shown. */
    public static final int LIMIT = 50;

    private final transient GitService git;
    private final CommitTableModel model;
    private final PlaceholderTable table;
    private final transient LatestLoader<HistoryData> loader;

    public HistoryPane(GitService git) {
        this(git, new CommitTableModel(CommitDates.system()));
    }

    private HistoryPane(GitService git, CommitTableModel model) {
        this(git, model, new PlaceholderTable(model));
    }

    private HistoryPane(GitService git, CommitTableModel model, PlaceholderTable table) {
        super(Messages.get("history.title"), Components.borderlessScroll(table));
        this.git = git;
        this.model = model;
        this.table = table;
        this.loader = new LatestLoader<>(this::show, this::showError);
        table.setShowGrid(false);
        table.getTableHeader().setReorderingAllowed(false);
        table.getColumnModel().getColumn(CommitTableModel.MESSAGE).setPreferredWidth(UIScale.scale(600));
        table.getColumnModel().getColumn(CommitTableModel.MESSAGE).setCellRenderer(new GraphMessageRenderer(model));
        table.setRowHeight(Math.max(table.getRowHeight(), UIScale.scale(22)));
        table.setIntercellSpacing(new Dimension(0, 0));
        table.getColumnModel().getColumn(CommitTableModel.AUTHOR).setPreferredWidth(UIScale.scale(180));
        table.getColumnModel().getColumn(CommitTableModel.DATE).setPreferredWidth(UIScale.scale(160));
        showRepository(Optional.empty());
    }

    /** Shows the newest commits of {@code workTree}, or a hint when no repository is open. Call on the EDT. */
    public void showRepository(Optional<Path> workTree) {
        model.setData(HistoryData.EMPTY);
        if (workTree.isEmpty()) {
            loader.cancel();
            table.setPlaceholder(Messages.get("history.placeholder.noRepository"));
            return;
        }
        table.setPlaceholder(Messages.get("history.placeholder.loading"));
        Path path = workTree.get();
        loader.load(() -> HistoryData.of(git.recentCommits(path, LIMIT)));
    }

    /** Re-reads the commits without clearing the table first. Call on the EDT. */
    public void refresh() {
        loader.refresh();
    }

    /** Messages of the shown rows (for tests). */
    List<String> messages() {
        return model.data().commits().stream().map(CommitInfo::message).toList();
    }

    private void show(HistoryData data) {
        if (!data.equals(model.data())) {
            int selected = table.getSelectedRow();
            String selectedId = selected >= 0 ? model.getCommit(selected).id() : null;
            model.setData(data);
            for (int row = 0; row < data.commits().size(); row++) {
                if (data.commits().get(row).id().equals(selectedId)) {
                    table.setRowSelectionInterval(row, row);
                }
            }
        }
        table.setPlaceholder(Messages.get("history.placeholder.empty"));
    }

    private void showError(Throwable error) {
        model.setData(HistoryData.EMPTY);
        table.setPlaceholder(Messages.format("history.placeholder.error", error.getMessage()));
    }
}
