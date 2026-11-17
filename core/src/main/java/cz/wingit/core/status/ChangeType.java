package cz.wingit.core.status;

/**
 * Kind of change of a file, either in the index (staged) or in the working tree (unstaged).
 */
public enum ChangeType {
    ADDED,
    MODIFIED,
    DELETED,
    /** Moved or renamed in the index; only reported as a staged change (see {@link FileChange#originalPath()}). */
    RENAMED,
    UNTRACKED,
    CONFLICTED
}
