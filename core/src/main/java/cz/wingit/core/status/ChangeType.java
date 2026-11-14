package cz.wingit.core.status;

/**
 * Kind of change of a file, either in the index (staged) or in the working tree (unstaged).
 */
public enum ChangeType {
    ADDED,
    MODIFIED,
    DELETED,
    UNTRACKED,
    CONFLICTED
}
