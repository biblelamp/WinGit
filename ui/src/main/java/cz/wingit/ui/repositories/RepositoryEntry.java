package cz.wingit.ui.repositories;

import java.nio.file.Path;

/**
 * One row of the repository list.
 *
 * @param branch  current branch, or {@code null} while unknown
 * @param changed whether the working tree has changes, or {@code null} while unknown
 * @param missing  whether the folder no longer exists on disk
 * @param unpushed commits of the current branch not pushed yet, or {@code null} while unknown
 */
record RepositoryEntry(Path path, String branch, Boolean changed, boolean missing, Integer unpushed) {

    RepositoryEntry(Path path, String branch, Boolean changed, boolean missing) {
        this(path, branch, changed, missing, null);
    }

    static RepositoryEntry unknown(Path path) {
        return new RepositoryEntry(path, null, null, false);
    }

    /** {@code true} if the current branch has commits that are not pushed yet. */
    boolean needsPush() {
        return unpushed != null && unpushed > 0;
    }

    RepositoryEntry withUnpushed(int newUnpushed) {
        return new RepositoryEntry(path, branch, changed, missing, newUnpushed);
    }

    String displayName() {
        Path name = path.getFileName();
        return name != null ? name.toString() : path.toString();
    }

    RepositoryEntry withBranch(String newBranch) {
        return new RepositoryEntry(path, newBranch, changed, missing, unpushed);
    }

    RepositoryEntry withChanged(boolean newChanged) {
        return new RepositoryEntry(path, branch, newChanged, missing, unpushed);
    }

    /** Marks the folder as missing (branch and changes become unknown) or present again. */
    RepositoryEntry withMissing(boolean newMissing) {
        if (newMissing == missing) {
            return this;
        }
        return newMissing ? new RepositoryEntry(path, null, null, true) : new RepositoryEntry(path, branch, changed, false, unpushed);
    }
}
