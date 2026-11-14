package cz.wingit.core.watch;

import java.io.IOException;
import java.util.function.Predicate;

import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.treewalk.FileTreeIterator;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.eclipse.jgit.treewalk.WorkingTreeIterator;
import org.eclipse.jgit.treewalk.filter.PathFilter;

/**
 * Tells whether a working tree path is ignored by Git (.gitignore files at any level,
 * .git/info/exclude, core.excludesFile). A path inside an ignored folder is ignored.
 * Paths that no longer exist count as not ignored. Not thread-safe.
 */
final class GitIgnoreChecker implements Predicate<String> {

    private final Repository repository;

    GitIgnoreChecker(Repository repository) {
        this.repository = repository;
    }

    @Override
    public boolean test(String relativePath) {
        try (TreeWalk walk = new TreeWalk(repository)) {
            walk.addTree(new FileTreeIterator(repository));
            walk.setFilter(PathFilter.create(relativePath));
            walk.setRecursive(false);
            while (walk.next()) {
                WorkingTreeIterator entry = walk.getTree(0, WorkingTreeIterator.class);
                if (entry != null && entry.isEntryIgnored()) {
                    return true;
                }
                if (walk.getPathString().equals(relativePath)) {
                    return false;
                }
                if (walk.isSubtree()) {
                    walk.enterSubtree();
                }
            }
            return false;
        } catch (IOException e) {
            return false;
        }
    }
}
