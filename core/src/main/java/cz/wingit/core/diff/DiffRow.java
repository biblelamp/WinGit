package cz.wingit.core.diff;

/**
 * One aligned row of a side-by-side diff. A side without a line (filler) has number 0 and
 * {@code null} text.
 *
 * @param leftLine  1-based line number on the left, or 0 for a filler
 * @param rightLine 1-based line number on the right, or 0 for a filler
 */
public record DiffRow(Type type, int leftLine, String leftText, int rightLine, String rightText) {

    public enum Type {
        /** Same line on both sides. */
        SAME,
        /** Line differs between the sides. */
        CHANGED,
        /** Line only on the left (right is a filler). */
        DELETED,
        /** Line only on the right (left is a filler). */
        ADDED
    }
}
