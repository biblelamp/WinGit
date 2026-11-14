package cz.wingit.core.graph;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;

import cz.wingit.core.graph.GraphRow.Segment;
import cz.wingit.core.log.CommitInfo;

class LaneLayoutTest {

    @Test
    void linearHistoryStaysInOneLane() {
        List<GraphRow> rows = LaneLayout.layout(List.of(c("c", "b"), c("b", "a"), c("a")));

        assertThat(rows).containsExactly(
                row(0, List.of(), List.of(s(0, 0)), 1),
                row(0, List.of(s(0, 0)), List.of(s(0, 0)), 1),
                row(0, List.of(s(0, 0)), List.of(), 1));
    }

    @Test
    void mergeOpensSecondLaneWhichJoinsAtCommonParent() {
        // m merges b into a; both come from root r
        List<GraphRow> rows = LaneLayout.layout(List.of(
                c("m", "a", "b"), c("a", "r"), c("b", "r"), c("r")));

        assertThat(rows).containsExactly(
                row(0, List.of(), List.of(s(0, 0), s(0, 1)), 2),
                row(0, List.of(s(0, 0), s(1, 1)), List.of(s(0, 0), s(1, 1)), 2),
                row(1, List.of(s(0, 0), s(1, 1)), List.of(s(0, 0), s(1, 0)), 2),
                row(0, List.of(s(0, 0)), List.of(), 1));
    }

    @Test
    void secondBranchTipStartsNewLaneWithoutLineFromAbove() {
        List<GraphRow> rows = LaneLayout.layout(List.of(c("x", "r"), c("y", "r"), c("r")));

        assertThat(rows.get(1)).isEqualTo(row(1, List.of(s(0, 0)), List.of(s(0, 0), s(1, 0)), 2));
        assertThat(rows.get(2)).isEqualTo(row(0, List.of(s(0, 0)), List.of(), 1));
    }

    @Test
    void freedLaneIsReusedOnlyInLaterRows() {
        // after b joins lane 0, lane 1 is free again; tip t then reuses it
        List<GraphRow> rows = LaneLayout.layout(List.of(
                c("m", "a", "b"), c("b", "a"), c("t", "a"), c("a")));

        assertThat(rows.get(1).lane()).isEqualTo(1);
        assertThat(rows.get(1).lower()).containsExactly(s(0, 0), s(1, 0));
        assertThat(rows.get(2).lane()).isEqualTo(1);
        assertThat(rows.get(2).upper()).containsExactly(s(0, 0));
    }

    @Test
    void parentsBeyondTheWindowKeepTheirLanesOpen() {
        List<GraphRow> rows = LaneLayout.layout(List.of(c("m", "a", "old")));

        assertThat(rows.get(0).lower()).containsExactly(s(0, 0), s(0, 1));
        assertThat(rows.get(0).laneCount()).isEqualTo(2);
    }

    private static CommitInfo c(String id, String... parents) {
        return new CommitInfo(id, Arrays.asList(parents), id, "Test", Instant.EPOCH, List.of());
    }

    private static Segment s(int from, int to) {
        return new Segment(from, to);
    }

    private static GraphRow row(int lane, List<Segment> upper, List<Segment> lower, int laneCount) {
        return new GraphRow(lane, upper, lower, laneCount);
    }
}
