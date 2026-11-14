package cz.wingit.core.status;

import java.util.List;

/**
 * Snapshot of the working tree status: changed, untracked and conflicting files
 * (ignored files are not included), sorted by path.
 */
public record RepositoryStatus(List<FileChange> changes) {

    public RepositoryStatus {
        changes = List.copyOf(changes);
    }

    public boolean isClean() {
        return changes.isEmpty();
    }
}
