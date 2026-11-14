package cz.wingit.core.status;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.eclipse.jgit.api.Status;

/**
 * Converts a JGit {@link Status} into {@link RepositoryStatus}: one entry per path,
 * combining its staged and unstaged change. Conflicts override other states.
 */
public final class StatusMapper {

    private StatusMapper() {
    }

    public static RepositoryStatus from(Status status) {
        Map<String, ChangeType[]> byPath = new TreeMap<>();

        putStaged(byPath, status.getAdded(), ChangeType.ADDED);
        putStaged(byPath, status.getChanged(), ChangeType.MODIFIED);
        putStaged(byPath, status.getRemoved(), ChangeType.DELETED);
        putUnstaged(byPath, status.getModified(), ChangeType.MODIFIED);
        putUnstaged(byPath, status.getMissing(), ChangeType.DELETED);
        putUnstaged(byPath, status.getUntracked(), ChangeType.UNTRACKED);
        for (String path : status.getConflicting()) {
            byPath.put(path, new ChangeType[] {null, ChangeType.CONFLICTED});
        }

        List<FileChange> changes = new ArrayList<>(byPath.size());
        byPath.forEach((path, types) -> changes.add(new FileChange(path, types[0], types[1])));
        return new RepositoryStatus(changes);
    }

    private static void putStaged(Map<String, ChangeType[]> byPath, Collection<String> paths, ChangeType type) {
        for (String path : paths) {
            byPath.computeIfAbsent(path, p -> new ChangeType[2])[0] = type;
        }
    }

    private static void putUnstaged(Map<String, ChangeType[]> byPath, Collection<String> paths, ChangeType type) {
        for (String path : paths) {
            byPath.computeIfAbsent(path, p -> new ChangeType[2])[1] = type;
        }
    }
}
