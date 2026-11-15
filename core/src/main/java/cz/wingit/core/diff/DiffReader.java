package cz.wingit.core.diff;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.eclipse.jgit.diff.RawText;
import org.eclipse.jgit.dircache.DirCache;
import org.eclipse.jgit.dircache.DirCacheEntry;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.FileMode;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectLoader;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.treewalk.FileTreeIterator;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.eclipse.jgit.treewalk.TreeWalk.OperationType;
import org.eclipse.jgit.treewalk.WorkingTreeIterator;
import org.eclipse.jgit.treewalk.filter.PathFilter;

import cz.wingit.core.diff.FileVersion.Source;
import cz.wingit.core.status.ChangeType;
import cz.wingit.core.status.FileChange;

/**
 * Reads the two versions of a changed file with JGit and compares them.
 * <ul>
 *   <li>unstaged change: index (left) vs working tree (right);</li>
 *   <li>staged-only change: HEAD vs index;</li>
 *   <li>conflict: HEAD vs working tree (with conflict markers).</li>
 * </ul>
 * The working tree version goes through Git's "clean" conversion (core.autocrlf, .gitattributes),
 * exactly as {@code git add} would see it, so line endings are never normalized by us.
 */
public final class DiffReader {

    /** Larger versions are not shown. */
    public static final long MAX_BYTES = 5L * 1024 * 1024;

    private DiffReader() {
    }

    public static FileDiff read(Repository repository, FileChange change) throws IOException {
        Source leftSource;
        Source rightSource;
        if (change.unstaged() == ChangeType.CONFLICTED) {
            leftSource = Source.HEAD;
            rightSource = Source.WORKING_TREE;
        } else if (change.unstaged() != null) {
            leftSource = Source.INDEX;
            rightSource = Source.WORKING_TREE;
        } else {
            leftSource = Source.HEAD;
            rightSource = Source.INDEX;
        }
        FileVersion left = version(repository, change.path(), leftSource);
        FileVersion right = version(repository, change.path(), rightSource);
        boolean comparable = !left.binary() && !right.binary() && !left.tooLarge() && !right.tooLarge();
        List<DiffRow> rows = comparable
                ? SideBySide.compute(left.comparableText(), right.comparableText())
                : List.of();
        return new FileDiff(change.path(), left, right, rows);
    }

    static FileVersion version(Repository repository, String path, Source source) throws IOException {
        switch (source) {
            case HEAD:
                return fromHead(repository, path);
            case INDEX:
                return fromIndex(repository, path);
            case WORKING_TREE:
            default:
                return fromWorkingTree(repository, path);
        }
    }

    private static FileVersion fromHead(Repository repository, String path) throws IOException {
        ObjectId tree = repository.resolve(Constants.HEAD + "^{tree}");
        if (tree == null) {
            return FileVersion.missing(Source.HEAD); // no commits yet
        }
        try (TreeWalk walk = TreeWalk.forPath(repository, path, tree)) {
            if (walk == null) {
                return FileVersion.missing(Source.HEAD);
            }
            if (walk.getFileMode(0) == FileMode.GITLINK) {
                return submodule(Source.HEAD, walk.getObjectId(0));
            }
            return fromBlob(repository, Source.HEAD, walk.getObjectId(0));
        }
    }

    private static FileVersion fromIndex(Repository repository, String path) throws IOException {
        DirCache index = repository.readDirCache();
        DirCacheEntry entry = index.getEntry(path);
        if (entry == null) {
            return FileVersion.missing(Source.INDEX);
        }
        if (entry.getFileMode() == FileMode.GITLINK) {
            return submodule(Source.INDEX, entry.getObjectId());
        }
        return fromBlob(repository, Source.INDEX, entry.getObjectId());
    }

    private static FileVersion fromWorkingTree(Repository repository, String path) throws IOException {
        try (TreeWalk walk = new TreeWalk(repository)) {
            walk.setOperationType(OperationType.CHECKIN_OP);
            walk.addTree(new FileTreeIterator(repository));
            walk.setFilter(PathFilter.create(path));
            walk.setRecursive(true);
            while (walk.next()) {
                if (!walk.getPathString().equals(path)) {
                    continue;
                }
                WorkingTreeIterator entry = walk.getTree(0, WorkingTreeIterator.class);
                if (entry.getEntryLength() > MAX_BYTES) {
                    return new FileVersion(Source.WORKING_TREE, true, null, false, true);
                }
                try (InputStream in = entry.openEntryStream()) {
                    return decode(Source.WORKING_TREE, in.readAllBytes());
                }
            }
            return FileVersion.missing(Source.WORKING_TREE);
        }
    }

    private static FileVersion fromBlob(Repository repository, Source source, ObjectId blob) throws IOException {
        ObjectLoader loader = repository.open(blob, Constants.OBJ_BLOB);
        if (loader.getSize() > MAX_BYTES) {
            return new FileVersion(source, true, null, false, true);
        }
        return decode(source, loader.getCachedBytes(Integer.MAX_VALUE));
    }

    private static FileVersion submodule(Source source, ObjectId commit) {
        return new FileVersion(source, true, "Subproject commit " + commit.name() + "\n", false, false);
    }

    private static FileVersion decode(Source source, byte[] bytes) {
        if (RawText.isBinary(bytes)) {
            return new FileVersion(source, true, null, true, false);
        }
        return new FileVersion(source, true, new String(bytes, StandardCharsets.UTF_8), false, false);
    }
}
