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
 * @param revision for {@link Source#COMMIT} and {@link Source#PARENT}, the full id of that commit
 *                 ({@code null} for the missing parent of a root commit); otherwise {@code null}
 */
public record FileVersion(Source source, boolean exists, String text, boolean binary, boolean tooLarge,
                          String revision) {

    public enum Source {
        HEAD,
        INDEX,
        WORKING_TREE,
        /** A commit shown in the history. */
        COMMIT,
        /** The first parent of the commit shown in the history. */
        PARENT
    }

    /** A version without a revision (working tree, index, HEAD). */
    public FileVersion(Source source, boolean exists, String text, boolean binary, boolean tooLarge) {
        this(source, exists, text, binary, tooLarge, null);
    }

    static FileVersion missing(Source source) {
        return new FileVersion(source, false, null, false, false);
    }

    /** Same content, labeled as the version in commit {@code commitId}. */
    FileVersion inRevision(String commitId) {
        return new FileVersion(source, exists, text, binary, tooLarge, commitId);
    }

    /** Text to compare: empty for a missing file. */
    String comparableText() {
        return text == null ? "" : text;
    }
}
