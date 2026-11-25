package cz.wingit.ui.history;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;
import java.util.function.Supplier;

import javax.swing.SwingWorker;

import cz.wingit.core.graph.GraphRow;
import cz.wingit.core.graph.LaneLayout;
import cz.wingit.core.log.CommitInfo;
import cz.wingit.core.log.HistoryReader;

/**
 * Reads a whole history in the background and delivers it on the EDT in portions, graph included.
 * <ul>
 *   <li>{@link #load} shows the history as it is read: a small first portion quickly, then larger ones.</li>
 *   <li>{@link #refresh} first checks the history's version (branches, tags, HEAD): if it is the one the
 *       shown history was read at, nothing is read. Otherwise the history is read again quietly and
 *       delivered as one replacing portion at the end, so the shown rows do not shrink and grow back.
 *       If a read is running, the refresh is repeated once after it.</li>
 * </ul>
 * Results of older reads are dropped. All methods must be called on the EDT.
 */
final class HistoryStreamLoader {

    /** Commits in the first portion: about a screenful or two, shown almost at once. */
    static final int FIRST_PORTION = 500;
    /** Commits in the following portions. */
    static final int PORTION = 5000;

    /**
     * Commits read in one go, with their graph rows.
     *
     * @param replace  the rows replace all shown ones (first portion of a load, or the result of a refresh)
     * @param complete the whole history has been read
     * @param version  for the complete portion, the history's version it was read at; otherwise {@code null}
     */
    record Portion(HistoryData data, boolean replace, boolean complete, String version) {
    }

    private final Consumer<Portion> onPortion;
    private final Consumer<Throwable> onError;
    private SwingWorker<Void, Portion> worker;
    private Supplier<HistoryReader> source;
    private Supplier<String> versionSource;
    /** Version the shown history was completely read at; {@code null} if none yet. */
    private String shownVersion;
    private int generation;
    private boolean refreshQueued;

    HistoryStreamLoader(Consumer<Portion> onPortion, Consumer<Throwable> onError) {
        this.onPortion = onPortion;
        this.onError = onError;
    }

    /**
     * Starts reading from {@code open}; the history is shown as it comes. {@code version} gives the
     * history's current version (see {@code GitService.historyVersion}). Both are called in the background.
     */
    void load(Supplier<HistoryReader> open, Supplier<String> version) {
        cancel();
        versionSource = version;
        start(open, false);
    }

    /** Reads the last source again and replaces the shown history once it is read completely. */
    void refresh() {
        if (source == null) {
            return;
        }
        if (isRunning()) {
            refreshQueued = true;
            return;
        }
        start(source, true);
    }

    /** True while a read (or the version check of a refresh) is running. */
    boolean isRunning() {
        return worker != null && !worker.isDone();
    }

    /** Drops any running read and forgets the source. */
    void cancel() {
        generation++;
        refreshQueued = false;
        source = null;
        shownVersion = null;
        if (worker != null) {
            worker.cancel(false); // the read stops before its next portion and closes the repository
            worker = null;
        }
    }

    private void start(Supplier<HistoryReader> open, boolean quiet) {
        generation++; // results of a running read are dropped
        refreshQueued = false;
        if (worker != null) {
            worker.cancel(false);
        }
        source = open;
        Supplier<String> version = versionSource;
        String known = shownVersion;
        int request = generation;
        worker = new SwingWorker<>() {
            @Override
            protected Void doInBackground() {
                // read before the commits: if refs move meanwhile, the next refresh sees a newer version
                String current = version.get();
                if (quiet && current.equals(known)) {
                    return null; // nothing the history depends on has changed
                }
                LaneLayout layout = new LaneLayout();
                List<CommitInfo> all = new ArrayList<>();
                List<GraphRow> allRows = new ArrayList<>();
                try (HistoryReader reader = open.get()) {
                    boolean first = true;
                    while (!isCancelled()) {
                        List<CommitInfo> commits = reader.next(first && !quiet ? FIRST_PORTION : PORTION);
                        if (commits.isEmpty()) {
                            break;
                        }
                        List<GraphRow> rows = layout.append(commits);
                        if (quiet) {
                            all.addAll(commits);
                            allRows.addAll(rows);
                        } else {
                            publish(new Portion(new HistoryData(commits, rows), first, false, null));
                        }
                        first = false;
                    }
                }
                if (!isCancelled()) {
                    // the last (or, after a refresh, only) portion marks the history as complete
                    publish(new Portion(new HistoryData(all, allRows), quiet, true, current));
                }
                return null;
            }

            @Override
            protected void process(List<Portion> portions) {
                if (request != generation) {
                    return;
                }
                for (Portion portion : portions) {
                    if (portion.complete()) {
                        shownVersion = portion.version();
                    }
                    onPortion.accept(portion);
                }
            }

            @Override
            protected void done() {
                if (request != generation || isCancelled()) {
                    return;
                }
                try {
                    get();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } catch (ExecutionException e) {
                    onError.accept(e.getCause() != null ? e.getCause() : e);
                }
                if (refreshQueued) {
                    refreshQueued = false;
                    start(open, true);
                }
            }
        };
        worker.execute();
    }
}
