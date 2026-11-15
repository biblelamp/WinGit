package cz.wingit.core.diff;

import java.util.ArrayList;
import java.util.List;

import com.github.difflib.DiffUtils;
import com.github.difflib.patch.AbstractDelta;
import com.github.difflib.patch.Patch;

/**
 * Aligns two texts line by line (pure function). Within a changed block the first lines are
 * paired as CHANGED, the rest become DELETED or ADDED rows with a filler on the other side.
 * Lines are compared exactly (a changed line ending is a change); a trailing CR is only hidden in
 * the displayed text.
 */
public final class SideBySide {

    private SideBySide() {
    }

    public static List<DiffRow> compute(String left, String right) {
        List<String> a = lines(left);
        List<String> b = lines(right);
        Patch<String> patch = DiffUtils.diff(a, b);

        List<DiffRow> rows = new ArrayList<>(Math.max(a.size(), b.size()));
        int i = 0; // next left index
        int j = 0; // next right index
        for (AbstractDelta<String> delta : patch.getDeltas()) {
            int leftStart = delta.getSource().getPosition();
            int rightStart = delta.getTarget().getPosition();
            while (i < leftStart && j < rightStart) {
                rows.add(new DiffRow(DiffRow.Type.SAME, i + 1, show(a.get(i)), j + 1, show(b.get(j))));
                i++;
                j++;
            }
            int leftCount = delta.getSource().size();
            int rightCount = delta.getTarget().size();
            int paired = Math.min(leftCount, rightCount);
            for (int k = 0; k < paired; k++) {
                rows.add(new DiffRow(DiffRow.Type.CHANGED, i + 1, show(a.get(i)), j + 1, show(b.get(j))));
                i++;
                j++;
            }
            for (int k = paired; k < leftCount; k++) {
                rows.add(new DiffRow(DiffRow.Type.DELETED, i + 1, show(a.get(i)), 0, null));
                i++;
            }
            for (int k = paired; k < rightCount; k++) {
                rows.add(new DiffRow(DiffRow.Type.ADDED, 0, null, j + 1, show(b.get(j))));
                j++;
            }
        }
        while (i < a.size() && j < b.size()) {
            rows.add(new DiffRow(DiffRow.Type.SAME, i + 1, show(a.get(i)), j + 1, show(b.get(j))));
            i++;
            j++;
        }
        return rows;
    }

    /** Splits on LF; a trailing newline does not create an extra empty line; "" has no lines. */
    static List<String> lines(String text) {
        List<String> lines = new ArrayList<>();
        if (text.isEmpty()) {
            return lines;
        }
        int start = 0;
        for (int k = 0; k < text.length(); k++) {
            if (text.charAt(k) == '\n') {
                lines.add(text.substring(start, k));
                start = k + 1;
            }
        }
        if (start < text.length()) {
            lines.add(text.substring(start));
        }
        return lines;
    }

    /** Lines ending with CRLF: don't show the CR as content. */
    private static String show(String line) {
        return line.endsWith("\r") ? line.substring(0, line.length() - 1) : line;
    }
}
