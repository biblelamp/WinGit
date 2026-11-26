package cz.wingit.core.merge;

import java.util.List;

import cz.wingit.core.log.CommitInfo;

/**
 * What merging a branch into the current one would do, read before the merge is started.
 *
 * @param kind          how the merge would end (if there are no conflicts)
 * @param incoming      the newest commits the branch would bring, newest first (at most the limit asked for)
 * @param incomingCount how many commits the branch would bring, counted up to {@link #COUNT_LIMIT}
 * @param currentOnly   how many commits the current branch has that the merged one does not, counted up to
 *                      {@link #COUNT_LIMIT}; 0 means the current branch can simply be moved forward
 * @param changedFiles  how many files the branch changed since the common commit (what the merge brings in)
 * @param localChanges  files with changes not committed yet (staged or not; new untracked files not counted)
 */
public record MergePreview(Kind kind, List<CommitInfo> incoming, int incomingCount, int currentOnly,
                           int changedFiles, int localChanges) {

    /** Commits are counted up to this number; a count equal to it means "this many or more". */
    public static final int COUNT_LIMIT = 10_000;

    /** How the merge would end. */
    public enum Kind {
        /** Nothing to merge: the current branch already contains all commits of the branch. */
        UP_TO_DATE,
        /** The current branch only needs to move forward to the branch (unless a merge commit is forced). */
        FAST_FORWARD,
        /** Both branches have their own commits: a merge commit joins them. */
        MERGE_COMMIT,
        /** The branches share no commit at all; Git refuses such a merge. */
        UNRELATED
    }

    public MergePreview {
        incoming = List.copyOf(incoming);
    }
}
