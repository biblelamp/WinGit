package cz.wingit.ui.repositories;

import java.nio.file.Path;

/**
 * One row of the repository list.
 *
 * @param branch current branch, or {@code null} while unknown
 */
record RepositoryEntry(Path path, String branch) {

    String displayName() {
        Path name = path.getFileName();
        return name != null ? name.toString() : path.toString();
    }

    RepositoryEntry withBranch(String newBranch) {
        return new RepositoryEntry(path, newBranch);
    }
}
