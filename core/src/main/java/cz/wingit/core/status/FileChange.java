package cz.wingit.core.status;

import java.util.Objects;

/**
 * A changed file of the working tree.
 *
 * @param path     repository-relative path with {@code /} separators (as Git reports it)
 * @param staged   change recorded in the index relative to HEAD, or {@code null}
 * @param unstaged change in the working tree relative to the index, or {@code null}
 */
public record FileChange(String path, ChangeType staged, ChangeType unstaged) {

    public FileChange {
        Objects.requireNonNull(path, "path");
        if (staged == null && unstaged == null) {
            throw new IllegalArgumentException("Unchanged file: " + path);
        }
    }

    /** Last path segment. */
    public String fileName() {
        return path.substring(path.lastIndexOf('/') + 1);
    }

    /** Parent folder ({@code /}-separated), empty for files in the repository root. */
    public String directory() {
        int slash = path.lastIndexOf('/');
        return slash < 0 ? "" : path.substring(0, slash);
    }
}
