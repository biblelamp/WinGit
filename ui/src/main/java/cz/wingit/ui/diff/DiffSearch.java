package cz.wingit.ui.diff;

import java.util.ArrayList;
import java.util.List;

import cz.wingit.core.diff.DiffRow;

/** Plain-text search in both sides of a diff, in reading order: by row, the left side first. */
final class DiffSearch {

    /** One occurrence: row of the diff, side, and character range within that side's line of the row. */
    record Match(int row, boolean left, int start, int end) {
    }

    private DiffSearch() {
    }

    /** All non-overlapping occurrences of {@code query}; none for an empty query. */
    static List<Match> find(List<DiffRow> rows, String query, boolean matchCase) {
        List<Match> matches = new ArrayList<>();
        if (query == null || query.isEmpty()) {
            return matches;
        }
        for (int row = 0; row < rows.size(); row++) {
            find(matches, row, true, rows.get(row).leftText(), query, matchCase);
            find(matches, row, false, rows.get(row).rightText(), query, matchCase);
        }
        return matches;
    }

    private static void find(List<Match> matches, int row, boolean left, String text, String query,
                             boolean matchCase) {
        if (text == null) {
            return;
        }
        int length = query.length();
        int i = 0;
        while (i + length <= text.length()) {
            if (text.regionMatches(!matchCase, i, query, 0, length)) {
                matches.add(new Match(row, left, i, i + length));
                i += length;
            } else {
                i++;
            }
        }
    }

    /** Index of the first match at or below {@code row}, wrapping to the first one; -1 without matches. */
    static int firstFrom(List<Match> matches, int row) {
        if (matches.isEmpty()) {
            return -1;
        }
        for (int i = 0; i < matches.size(); i++) {
            if (matches.get(i).row() >= row) {
                return i;
            }
        }
        return 0;
    }
}
