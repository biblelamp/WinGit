package cz.wingit.core.log;

import java.util.Objects;

import cz.wingit.core.status.ChangeType;

/**
 * A file changed by a commit, compared with the commit's first parent (with an empty tree for a root commit).
 *
 * @param path         repository-relative path in the commit, {@code /}-separated; for a deleted file the
 *                     path it had in the parent
 * @param change       {@link ChangeType#ADDED}, {@link ChangeType#MODIFIED}, {@link ChangeType#DELETED} or
 *                     {@link ChangeType#RENAMED}
 * @param originalPath for a rename, the path in the parent; otherwise {@code null}
 */
public record CommitFile(String path, ChangeType change, String originalPath) {

    public CommitFile {
        Objects.requireNonNull(path, "path");
        Objects.requireNonNull(change, "change");
        if ((change == ChangeType.RENAMED) != (originalPath != null)) {
            throw new IllegalArgumentException("Original path must be given exactly for a rename: " + path);
        }
    }

    /** A change that is not a rename. */
    public CommitFile(String path, ChangeType change) {
        this(path, change, null);
    }

    /** Path of the file in the parent: the original path of a rename, otherwise {@link #path()}. */
    public String parentPath() {
        return originalPath == null ? path : originalPath;
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
