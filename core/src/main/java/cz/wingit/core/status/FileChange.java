package cz.wingit.core.status;

import java.util.List;
import java.util.Objects;

/**
 * A changed file of the working tree.
 *
 * @param path         repository-relative path with {@code /} separators (as Git reports it)
 * @param staged       change recorded in the index relative to HEAD, or {@code null}
 * @param unstaged     change in the working tree relative to the index, or {@code null}
 * @param originalPath for a staged {@link ChangeType#RENAMED rename}, the path in HEAD; otherwise {@code null}
 */
public record FileChange(String path, ChangeType staged, ChangeType unstaged, String originalPath) {

    public FileChange {
        Objects.requireNonNull(path, "path");
        if (staged == null && unstaged == null) {
            throw new IllegalArgumentException("Unchanged file: " + path);
        }
        if ((staged == ChangeType.RENAMED) != (originalPath != null)) {
            throw new IllegalArgumentException("Original path must be given exactly for a staged rename: " + path);
        }
        if (unstaged == ChangeType.RENAMED) {
            throw new IllegalArgumentException("Renames are only reported as staged: " + path);
        }
    }

    /** A change that is not a rename. */
    public FileChange(String path, ChangeType staged, ChangeType unstaged) {
        this(path, staged, unstaged, null);
    }

    /** A staged rename from {@code originalPath}, possibly changed again in the working tree. */
    public static FileChange renamed(String originalPath, String path, ChangeType unstaged) {
        return new FileChange(path, ChangeType.RENAMED, unstaged, Objects.requireNonNull(originalPath));
    }

    /** All paths this change touches: the original path of a rename first, then {@link #path()}. */
    public List<String> paths() {
        return originalPath == null ? List.of(path) : List.of(originalPath, path);
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
