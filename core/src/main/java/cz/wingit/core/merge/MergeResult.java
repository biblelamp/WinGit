package cz.wingit.core.merge;

import java.util.List;

/**
 * How a merge ended.
 *
 * @param outcome   what happened
 * @param conflicts repository-relative paths with conflicts (only for {@link Outcome#CONFLICTS})
 */
public record MergeResult(Outcome outcome, List<String> conflicts) {

    /** The possible results of a merge. */
    public enum Outcome {
        /** Nothing to do: the branch is already contained in the current one. */
        UP_TO_DATE,
        /** The current branch was moved forward to the merged one; no merge commit. */
        FAST_FORWARD,
        /** A merge commit was created. */
        MERGED,
        /** The merge stopped with conflicts; the repository is in the middle of the merge. */
        CONFLICTS
    }

    public MergeResult {
        conflicts = List.copyOf(conflicts);
    }

    public static MergeResult of(Outcome outcome) {
        return new MergeResult(outcome, List.of());
    }
}
