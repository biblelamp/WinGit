package cz.wingit.core.refs;

import java.util.List;

import cz.wingit.core.cli.LoggedCommand;

/**
 * How a branch switch with automatic stash ended (stash the local changes, switch, apply them again).
 *
 * @param outcome        what happened
 * @param conflicts      repository-relative paths with conflicts after applying the stash, else empty
 * @param log            every Git command run, in order, with its output
 * @param previousBranch the branch checked out before the switch, {@code null} if unknown or detached
 * @param stash          id of the stash still holding the changes, {@code null} if none was kept
 */
public record StashedSwitch(Outcome outcome, List<String> conflicts, List<LoggedCommand> log,
                            String previousBranch, String stash) {

    /** The possible results. */
    public enum Outcome {
        /** Switched, and the changes were applied on the new branch; the stash is gone. */
        RESTORED,
        /** Switched; the changes stay in the stash, as asked (not brought back). */
        STASHED,
        /** Switched, but the changes clash with the new branch: conflicts to resolve, the stash is kept. */
        CONFLICTS,
        /** Switched, but the changes could not be applied at all; they stay in the stash. */
        STASH_KEPT,
        /** The switch failed; the changes were applied back on the original branch. */
        NOT_SWITCHED,
        /** The switch failed and the changes could not be applied back; they stay in the stash. */
        NOT_SWITCHED_STASH_KEPT,
        /** The changes could not be stashed; nothing changed. */
        NOT_STASHED
    }

    public StashedSwitch {
        conflicts = List.copyOf(conflicts);
        log = List.copyOf(log);
    }

    /** A result without a previous branch or kept stash. */
    public StashedSwitch(Outcome outcome, List<String> conflicts, List<LoggedCommand> log) {
        this(outcome, conflicts, log, null, null);
    }

    /** {@code true} if the branch was switched. */
    public boolean switched() {
        return outcome == Outcome.RESTORED || outcome == Outcome.STASHED || outcome == Outcome.CONFLICTS
                || outcome == Outcome.STASH_KEPT;
    }

    /**
     * {@code true} if the switch can be taken back: it ended in conflicts, and both the previous branch and
     * the stash holding the changes are known.
     */
    public boolean canGoBack() {
        return outcome == Outcome.CONFLICTS && previousBranch != null && stash != null;
    }
}
