package cz.wingit.core.diff;

import java.util.List;

/**
 * Two versions of a file and their aligned line comparison. {@code rows} is empty when either
 * side is binary or too large.
 */
public record FileDiff(String path, FileVersion left, FileVersion right, List<DiffRow> rows) {

    public FileDiff {
        rows = List.copyOf(rows);
    }

    public boolean isBinary() {
        return left.binary() || right.binary();
    }

    public boolean isTooLarge() {
        return left.tooLarge() || right.tooLarge();
    }

    /** Whether any row differs. */
    public boolean hasChanges() {
        return rows.stream().anyMatch(row -> row.type() != DiffRow.Type.SAME);
    }
}
