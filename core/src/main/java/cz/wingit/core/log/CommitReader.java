package cz.wingit.core.log;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.diff.RenameDetector;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.treewalk.CanonicalTreeParser;
import org.eclipse.jgit.treewalk.EmptyTreeIterator;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.eclipse.jgit.treewalk.filter.TreeFilter;

import cz.wingit.core.status.ChangeType;

/**
 * Reads one commit with JGit: its metadata, full message and the files it changed compared with its
 * first parent (renames detected as {@code git diff} does by default).
 */
public final class CommitReader {

    private CommitReader() {
    }

    /** @param id full or abbreviated commit id */
    public static CommitDetails read(Repository repository, String id) throws IOException {
        ObjectId objectId = repository.resolve(id + "^{commit}");
        if (objectId == null) {
            throw new IOException("Unknown commit " + id);
        }
        try (RevWalk walk = new RevWalk(repository)) {
            RevCommit commit = walk.parseCommit(objectId);
            List<String> parents = new ArrayList<>(commit.getParentCount());
            for (RevCommit parent : commit.getParents()) {
                parents.add(parent.getName());
            }
            RevCommit firstParent = commit.getParentCount() > 0 ? walk.parseCommit(commit.getParent(0)) : null;
            PersonIdent author = commit.getAuthorIdent();
            PersonIdent committer = commit.getCommitterIdent();
            return new CommitDetails(commit.getName(), parents,
                    new CommitDetails.Person(author.getName(), author.getEmailAddress()),
                    author.getWhenAsInstant(),
                    new CommitDetails.Person(committer.getName(), committer.getEmailAddress()),
                    committer.getWhenAsInstant(),
                    commit.getFullMessage(),
                    files(repository, firstParent, commit));
        }
    }

    private static List<CommitFile> files(Repository repository, RevCommit parent, RevCommit commit)
            throws IOException {
        try (ObjectReader reader = repository.newObjectReader(); TreeWalk walk = new TreeWalk(reader)) {
            if (parent == null) {
                walk.addTree(new EmptyTreeIterator());
            } else {
                walk.addTree(new CanonicalTreeParser(null, reader, parent.getTree()));
            }
            walk.addTree(new CanonicalTreeParser(null, reader, commit.getTree()));
            walk.setRecursive(true);
            walk.setFilter(TreeFilter.ANY_DIFF);
            RenameDetector renames = new RenameDetector(repository);
            renames.addAll(DiffEntry.scan(walk));
            List<CommitFile> files = new ArrayList<>();
            for (DiffEntry entry : renames.compute()) {
                files.add(file(entry));
            }
            files.sort(Comparator.comparing(CommitFile::path));
            return files;
        }
    }

    private static CommitFile file(DiffEntry entry) {
        switch (entry.getChangeType()) {
            case ADD:
            case COPY:
                return new CommitFile(entry.getNewPath(), ChangeType.ADDED);
            case DELETE:
                return new CommitFile(entry.getOldPath(), ChangeType.DELETED);
            case RENAME:
                return new CommitFile(entry.getNewPath(), ChangeType.RENAMED, entry.getOldPath());
            case MODIFY:
            default:
                return new CommitFile(entry.getNewPath(), ChangeType.MODIFIED);
        }
    }
}
