package cz.wingit.ui.diff;

import java.util.ArrayList;
import java.util.List;

import cz.wingit.core.diff.DiffRow;

/**
 * Changed fragments of a diff for "next/previous change": a fragment is a run of rows that are not
 * {@link DiffRow.Type#SAME}; it is identified by the index of its first row.
 */
final class DiffChanges {

    private DiffChanges() {
    }

    /**
     * A changed fragment: rows {@code start} (inclusive) to {@code end} (exclusive), and whether it removes
     * lines of the left side and adds lines of the right side (a changed line does both).
     */
    record Fragment(int start, int end, boolean removes, boolean adds) {
    }

    /** The changed fragments, in order. */
    static List<Fragment> fragments(List<DiffRow> rows) {
        List<Fragment> fragments = new ArrayList<>();
        int start = -1;
        boolean removes = false;
        boolean adds = false;
        for (int i = 0; i <= rows.size(); i++) {
            DiffRow.Type type = i < rows.size() ? rows.get(i).type() : DiffRow.Type.SAME;
            if (type == DiffRow.Type.SAME) {
                if (start >= 0) {
                    fragments.add(new Fragment(start, i, removes, adds));
                    start = -1;
                }
                continue;
            }
            if (start < 0) {
                start = i;
                removes = false;
                adds = false;
            }
            removes |= type != DiffRow.Type.ADDED;
            adds |= type != DiffRow.Type.DELETED;
        }
        return fragments;
    }

    /** Index of the first row of each changed fragment, in order. */
    static List<Integer> starts(List<DiffRow> rows) {
        List<Integer> starts = new ArrayList<>();
        for (int i = 0; i < rows.size(); i++) {
            boolean changed = rows.get(i).type() != DiffRow.Type.SAME;
            boolean previousChanged = i > 0 && rows.get(i - 1).type() != DiffRow.Type.SAME;
            if (changed && !previousChanged) {
                starts.add(i);
            }
        }
        return starts;
    }

    /** Index (into {@code starts}) of the first fragment after {@code row}, or -1. */
    static int next(List<Integer> starts, int row) {
        for (int i = 0; i < starts.size(); i++) {
            if (starts.get(i) > row) {
                return i;
            }
        }
        return -1;
    }

    /** Index (into {@code starts}) of the last fragment before {@code row}, or -1. */
    static int previous(List<Integer> starts, int row) {
        for (int i = starts.size() - 1; i >= 0; i--) {
            if (starts.get(i) < row) {
                return i;
            }
        }
        return -1;
    }
}
