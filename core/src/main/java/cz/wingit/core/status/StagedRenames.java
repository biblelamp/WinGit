package cz.wingit.core.status;

import java.io.IOException;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import org.eclipse.jgit.api.Status;
import org.eclipse.jgit.api.errors.CanceledException;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.diff.RenameDetector;
import org.eclipse.jgit.dircache.DirCacheIterator;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.NullProgressMonitor;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.eclipse.jgit.treewalk.filter.PathFilterGroup;

/**
 * Finds renames recorded in the index (HEAD to index), as {@code git status} shows them. JGit's status
 * reports such a rename as a staged deletion plus a staged addition; only those paths are compared.
 */
public final class StagedRenames {

    private StagedRenames() {
    }

    /** New path to original path, for each staged rename; empty if there is nothing to pair. */
    public static Map<String, String> find(Repository repository, Status status) throws IOException {
        if (status.getAdded().isEmpty() || status.getRemoved().isEmpty()) {
            return Map.of();
        }
        ObjectId head = repository.resolve(Constants.HEAD + "^{tree}");
        if (head == null) {
            return Map.of();
        }
        Set<String> paths = new LinkedHashSet<>(status.getAdded());
        paths.addAll(status.getRemoved());
        Map<String, String> renames = new HashMap<>();
        try (ObjectReader reader = repository.newObjectReader(); TreeWalk walk = new TreeWalk(repository, reader)) {
            walk.setRecursive(true);
            walk.addTree(head);
            walk.addTree(new DirCacheIterator(repository.readDirCache()));
            walk.setFilter(PathFilterGroup.createFromStrings(paths));
            RenameDetector detector = new RenameDetector(repository);
            detector.addAll(DiffEntry.scan(walk));
            for (DiffEntry entry : detector.compute(reader, NullProgressMonitor.INSTANCE)) {
                if (entry.getChangeType() == DiffEntry.ChangeType.RENAME) {
                    renames.put(entry.getNewPath(), entry.getOldPath());
                }
            }
        } catch (CanceledException e) {
            throw new IllegalStateException("Rename detection cannot be cancelled here", e); // no-op monitor
        }
        return renames;
    }
}
