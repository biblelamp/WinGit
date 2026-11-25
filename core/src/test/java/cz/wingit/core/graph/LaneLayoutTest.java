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

    @Test
    void edgesFromUnpushedCommitsAreFlaggedUpToTheParent() {
        // u2 -> u1 -> p (pushed) -> r; side branch tip x (pushed) passes the unpushed rows
        List<GraphRow> rows = LaneLayout.layout(List.of(
                unpushed("u2", "u1"), c("x", "r"), unpushed("u1", "p"), c("p", "r"), c("r")));

        assertThat(rows.get(0).lower()).containsExactly(new Segment(0, 0, true));
        assertThat(rows.get(1).upper()).containsExactly(new Segment(0, 0, true));
        assertThat(rows.get(1).lower()).containsExactly(new Segment(0, 0, true), new Segment(1, 1, false));
        assertThat(rows.get(2).upper()).containsExactly(new Segment(0, 0, true), new Segment(1, 1, false));
        assertThat(rows.get(2).lower()).containsExactly(new Segment(0, 0, true), new Segment(1, 1, false));
        // the edge u1 -> p ends in p's node; below p nothing is outgoing any more
        assertThat(rows.get(3).upper()).containsExactly(new Segment(0, 0, true), new Segment(1, 1, false));
        assertThat(rows.get(3).lower()).noneMatch(Segment::unpushed);
        assertThat(rows.get(4).upper()).noneMatch(Segment::unpushed);
    }

    @Test
    void historyLaidOutInPortionsGivesTheSameRowsAsAllAtOnce() {
        // two merges, a side branch tip, unpushed commits on top: lanes open across portion borders
        List<CommitInfo> commits = List.of(
                unpushed("u", "m2"), c("m2", "a", "f2"), c("x", "b"), c("f2", "f1"), c("a", "m1"),
                c("m1", "b", "f1"), c("f1", "r"), c("b", "r"), c("r"));
        List<GraphRow> whole = LaneLayout.layout(commits);

        for (int size = 1; size <= commits.size(); size++) {
            LaneLayout layout = new LaneLayout();
            List<GraphRow> rows = new java.util.ArrayList<>();
            for (int from = 0; from < commits.size(); from += size) {
                rows.addAll(layout.append(commits.subList(from, Math.min(commits.size(), from + size))));
            }
            assertThat(rows).as("portions of %d", size).isEqualTo(whole);
        }
    }

    private static CommitInfo unpushed(String id, String... parents) {
        return new CommitInfo(id, Arrays.asList(parents), id, "Test", Instant.EPOCH, List.of(), true);
    }

    private static CommitInfo c(String id, String... parents) {
        return new CommitInfo(id, Arrays.asList(parents), id, "Test", Instant.EPOCH, List.of(), false);
    }

    private static Segment s(int from, int to) {
        return new Segment(from, to);
    }

    private static GraphRow row(int lane, List<Segment> upper, List<Segment> lower, int laneCount) {
        return new GraphRow(lane, upper, lower, laneCount);
    }
}
