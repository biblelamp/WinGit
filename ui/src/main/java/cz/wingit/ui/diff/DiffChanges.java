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
