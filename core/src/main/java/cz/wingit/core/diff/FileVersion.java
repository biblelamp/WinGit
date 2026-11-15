package cz.wingit.core.diff;

/**
 * One side of a file comparison.
 *
 * @param source  where this version comes from
 * @param exists  {@code false} if the file does not exist there (added or deleted file)
 * @param text    content decoded as UTF-8 with LF line endings as Git stores it; {@code null} when the
 *                file does not exist, is binary or too large
 * @param binary  content looks binary (contains NUL bytes)
 * @param tooLarge content exceeds the size limit and was not read
 */
public record FileVersion(Source source, boolean exists, String text, boolean binary, boolean tooLarge) {

    public enum Source {
        HEAD,
        INDEX,
        WORKING_TREE
    }

    static FileVersion missing(Source source) {
        return new FileVersion(source, false, null, false, false);
    }

    /** Text to compare: empty for a missing file. */
    String comparableText() {
        return text == null ? "" : text;
    }
}
