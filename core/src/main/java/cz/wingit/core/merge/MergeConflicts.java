package cz.wingit.core.merge;

import java.util.List;

/**
 * Files that would get conflicts if a branch were merged into the current one, found before the merge.
 *
 * @param certainty how the files were found
 * @param files     repository-relative paths ({@code /}-separated); empty if the merge would be clean
 */
public record MergeConflicts(Certainty certainty, List<String> files) {

    /** How reliable the list is. */
    public enum Certainty {
        /** Git tried the merge in memory: exactly these files conflict. */
        EXACT,
        /**
         * Git is too old for a trial merge ({@code git merge-tree --write-tree} needs 2.38): these files
         * were changed differently on both branches, so they may conflict (Git often merges such files
         * on its own when different lines changed).
         */
        LIKELY
    }

    public MergeConflicts {
        files = List.copyOf(files);
    }

    public boolean isEmpty() {
        return files.isEmpty();
    }
}
