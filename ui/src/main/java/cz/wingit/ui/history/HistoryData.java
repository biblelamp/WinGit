package cz.wingit.ui.history;

import java.util.List;

import cz.wingit.core.graph.GraphRow;
import cz.wingit.core.graph.LaneLayout;
import cz.wingit.core.log.CommitInfo;

/**
 * Commits plus their graph rows (same order), computed together in the background.
 */
record HistoryData(List<CommitInfo> commits, List<GraphRow> graph) {

    static final HistoryData EMPTY = new HistoryData(List.of(), List.of());

    HistoryData {
        commits = List.copyOf(commits);
        graph = List.copyOf(graph);
    }

    static HistoryData of(List<CommitInfo> commits) {
        return new HistoryData(commits, LaneLayout.layout(commits));
    }

    int maxLaneCount() {
        return graph.stream().mapToInt(GraphRow::laneCount).max().orElse(0);
    }
}
