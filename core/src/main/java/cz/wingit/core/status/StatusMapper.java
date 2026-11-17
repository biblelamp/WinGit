package cz.wingit.core.status;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.eclipse.jgit.api.Status;

/**
 * Converts a JGit {@link Status} into {@link RepositoryStatus}: one entry per path,
 * combining its staged and unstaged change. Conflicts override other states. A staged rename
 * (see {@link StagedRenames}) becomes one entry at the new path instead of a deletion plus an addition.
 */
public final class StatusMapper {

    private StatusMapper() {
    }

    public static RepositoryStatus from(Status status) {
        return from(status, Map.of());
    }

    /** @param renames staged renames, new path to original path */
    public static RepositoryStatus from(Status status, Map<String, String> renames) {
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

        Map<String, String> originals = new HashMap<>();
        renames.forEach((newPath, oldPath) -> {
            ChangeType[] added = byPath.get(newPath);
            ChangeType[] removed = byPath.get(oldPath);
            if (added == null || removed == null
                    || added[0] != ChangeType.ADDED || removed[0] != ChangeType.DELETED) {
                return;
            }
            added[0] = ChangeType.RENAMED;
            originals.put(newPath, oldPath);
            if (removed[1] == null) {
                byPath.remove(oldPath);
            } else {
                removed[0] = null; // e.g. a new file created again at the old path: stays as untracked
            }
        });

        List<FileChange> changes = new ArrayList<>(byPath.size());
        byPath.forEach((path, types) -> changes.add(new FileChange(path, types[0], types[1], originals.get(path))));
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
