package cz.wingit.core;

/**
 * Whether the last commit of the checked-out branch can be amended, and its message to start from.
 *
 * @param state   whether amending is possible, or why not
 * @param message full message of the last commit; {@code null} unless {@link State#POSSIBLE}
 */
public record AmendCheck(State state, String message) {

    /** Whether the last commit can be amended, or why not. */
    public enum State {
        /** The last commit is on no remote-tracking branch: amending rewrites nothing shared. */
        POSSIBLE,
        /** The repository has no commit yet. */
        NO_COMMIT,
        /** The last commit is already on a remote-tracking branch, i.e. pushed (or fetched from elsewhere). */
        PUSHED,
        /** A merge, cherry-pick, revert, ... is in progress: its commit must be made first. */
        OPERATION_IN_PROGRESS
    }

    public AmendCheck {
        if ((state == State.POSSIBLE) != (message != null)) {
            throw new IllegalArgumentException("A message belongs only to an amendable commit");
        }
    }

    public static AmendCheck notPossible(State state) {
        return new AmendCheck(state, null);
    }

    public boolean possible() {
        return state == State.POSSIBLE;
    }
}
