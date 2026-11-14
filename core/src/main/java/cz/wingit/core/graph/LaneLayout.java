package cz.wingit.core.graph;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import cz.wingit.core.graph.GraphRow.Segment;
import cz.wingit.core.log.CommitInfo;

/**
 * Assigns commits to lanes for drawing the history graph. Pure and incremental-friendly:
 * each row depends only on the rows above it.
 * <p>
 * Rules: a lane "expects" a commit id. A commit is drawn in the first lane expecting it (or a new
 * lane for branch tips); every other lane expecting it merges into the node. Its first parent
 * continues in the node's lane unless another lane already expects it; further parents (merges)
 * get an existing or a new lane. Free lanes are reused, but not in the row that freed them, so a
 * line never enters and leaves the node through the same freed lane.
 */
public final class LaneLayout {

    private LaneLayout() {
    }

    /** Rows in the same order as {@code commits} (newest first). */
    public static List<GraphRow> layout(List<CommitInfo> commits) {
        List<String> lanes = new ArrayList<>(); // lane -> expected commit id, null = free
        List<GraphRow> rows = new ArrayList<>(commits.size());

        for (CommitInfo commit : commits) {
            String id = commit.id();
            List<Segment> upper = new ArrayList<>();
            List<Segment> lower = new ArrayList<>();

            int node = lanes.indexOf(id);
            boolean expected = node >= 0;
            if (!expected) {
                node = freeLane(lanes, Set.of());
            }

            Set<Integer> passing = new HashSet<>();
            Set<Integer> freed = new HashSet<>();
            for (int lane = 0; lane < lanes.size(); lane++) {
                String target = lanes.get(lane);
                if (target == null) {
                    continue;
                }
                if (target.equals(id)) {
                    upper.add(new Segment(lane, node));
                    lanes.set(lane, null);
                    if (lane != node) {
                        freed.add(lane);
                    }
                } else {
                    upper.add(new Segment(lane, lane));
                    passing.add(lane);
                }
            }

            List<String> parents = commit.parents();
            if (!parents.isEmpty()) {
                String first = parents.get(0);
                int existing = lanes.indexOf(first);
                if (existing >= 0) {
                    lower.add(new Segment(node, existing));
                } else {
                    set(lanes, node, first);
                    lower.add(new Segment(node, node));
                }
                for (String parent : parents.subList(1, parents.size())) {
                    int lane = lanes.indexOf(parent);
                    if (lane < 0) {
                        Set<Integer> blocked = new HashSet<>(freed);
                        blocked.add(node);
                        lane = freeLane(lanes, blocked);
                        set(lanes, lane, parent);
                    }
                    lower.add(new Segment(node, lane));
                }
            }
            for (int lane : passing) {
                lower.add(new Segment(lane, lane));
            }

            trimTrailingFree(lanes);
            rows.add(new GraphRow(node, sorted(upper), sorted(lower), laneCount(node, upper, lower)));
        }
        return rows;
    }

    /** First free lane not in {@code blocked}, or a new lane at the end. */
    private static int freeLane(List<String> lanes, Set<Integer> blocked) {
        for (int lane = 0; lane < lanes.size(); lane++) {
            if (lanes.get(lane) == null && !blocked.contains(lane)) {
                return lane;
            }
        }
        return lanes.size();
    }

    private static void set(List<String> lanes, int lane, String id) {
        while (lanes.size() <= lane) {
            lanes.add(null);
        }
        lanes.set(lane, id);
    }

    private static void trimTrailingFree(List<String> lanes) {
        while (!lanes.isEmpty() && lanes.get(lanes.size() - 1) == null) {
            lanes.remove(lanes.size() - 1);
        }
    }

    private static int laneCount(int node, List<Segment> upper, List<Segment> lower) {
        int max = node;
        for (Segment s : upper) {
            max = Math.max(max, Math.max(s.from(), s.to()));
        }
        for (Segment s : lower) {
            max = Math.max(max, Math.max(s.from(), s.to()));
        }
        return max + 1;
    }

    /** Stable order makes rows comparable in tests and repaint-friendly. */
    private static List<Segment> sorted(List<Segment> segments) {
        segments.sort((a, b) -> a.from() != b.from() ? Integer.compare(a.from(), b.from())
                : Integer.compare(a.to(), b.to()));
        return segments;
    }
}
