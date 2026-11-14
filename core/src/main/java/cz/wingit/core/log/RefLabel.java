package cz.wingit.core.log;

/**
 * A branch, tag or detached HEAD pointing at a commit, shown as a label in the history.
 *
 * @param name    short name, e.g. {@code main}, {@code origin/main}, {@code v1.0}, {@code HEAD}
 * @param current checked-out branch, or the detached HEAD
 */
public record RefLabel(String name, Kind kind, boolean current) {

    /** Declaration order is the display order on a commit. */
    public enum Kind {
        DETACHED_HEAD,
        LOCAL_BRANCH,
        REMOTE_BRANCH,
        TAG
    }
}
