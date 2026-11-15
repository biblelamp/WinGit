package cz.wingit.core.graph;

import java.util.List;

/**
 * How to paint the graph cell of one history row. Lanes are columns, numbered from 0 (left).
 *
 * @param lane      lane of this commit's node
 * @param upper     lines in the upper half of the row: from a lane at the top edge to a lane at the middle
 * @param lower     lines in the lower half: from a lane at the middle to a lane at the bottom edge
 * @param laneCount number of lanes this row needs to be fully visible
 */
public record GraphRow(int lane, List<Segment> upper, List<Segment> lower, int laneCount) {

    public GraphRow {
        upper = List.copyOf(upper);
        lower = List.copyOf(lower);
    }

    /**
     * A straight line between two lanes across one half of a row.
     *
     * @param unpushed the line belongs to an edge from an unpushed commit to its parent
     */
    public record Segment(int from, int to, boolean unpushed) {

        public Segment(int from, int to) {
            this(from, to, false);
        }
    }
}
