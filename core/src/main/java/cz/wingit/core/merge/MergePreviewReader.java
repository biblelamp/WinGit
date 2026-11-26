package cz.wingit.core.merge;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.diff.DiffFormatter;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevSort;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.revwalk.filter.RevFilter;
import org.eclipse.jgit.util.io.DisabledOutputStream;

import cz.wingit.core.GitException;
import cz.wingit.core.log.CommitInfo;

/** Reads a {@link MergePreview} with JGit; changes nothing in the repository. */
public final class MergePreviewReader {

    private MergePreviewReader() {
    }

    /**
     * @param ref          branch to merge into HEAD, e.g. {@code feature} or {@code origin/feature}
     * @param limit        how many incoming commits to list
     * @param localChanges passed through to the preview
     * @throws GitException if {@code ref} is not a commit
     */
    public static MergePreview read(Repository repository, String ref, int limit, int localChanges)
            throws IOException {
        if (limit < 0) {
            throw new IllegalArgumentException("limit must not be negative: " + limit);
        }
        ObjectId sourceId = repository.resolve(ref + "^{commit}");
        if (sourceId == null) {
            throw new GitException("Not a branch or commit: " + ref);
        }
        ObjectId headId = repository.resolve(Constants.HEAD + "^{commit}");
        try (RevWalk walk = new RevWalk(repository)) {
            RevCommit source = walk.parseCommit(sourceId);
            RevCommit head = headId == null ? null : walk.parseCommit(headId);

            List<CommitInfo> incoming = new ArrayList<>();
            int incomingCount = count(walk, source, head, limit, incoming);
            int currentOnly = head == null ? 0 : count(walk, head, source, 0, null);
            RevCommit base = head == null ? null : mergeBase(walk, head, source);

            MergePreview.Kind kind;
            if (incomingCount == 0) {
                kind = MergePreview.Kind.UP_TO_DATE;
            } else if (head == null || currentOnly == 0) {
                kind = MergePreview.Kind.FAST_FORWARD;
            } else if (base == null) {
                kind = MergePreview.Kind.UNRELATED;
            } else {
                kind = MergePreview.Kind.MERGE_COMMIT;
            }
            int changedFiles = kind == MergePreview.Kind.UP_TO_DATE ? 0
                    : changedFiles(repository, base, source);
            return new MergePreview(kind, incoming, incomingCount, currentOnly, changedFiles, localChanges);
        }
    }

    /**
     * Counts commits reachable from {@code start} but not from {@code exclude} (up to the count limit);
     * the newest {@code limit} of them go to {@code collect}, if given.
     */
    private static int count(RevWalk walk, RevCommit start, RevCommit exclude, int limit, List<CommitInfo> collect)
            throws IOException {
        walk.reset();
        walk.setRevFilter(RevFilter.ALL);
        walk.sort(RevSort.COMMIT_TIME_DESC);
        walk.markStart(start);
        if (exclude != null) {
            walk.markUninteresting(exclude);
        }
        int count = 0;
        for (RevCommit commit : walk) {
            if (collect != null && collect.size() < limit) {
                collect.add(info(commit));
            }
            if (++count == MergePreview.COUNT_LIMIT) {
                break;
            }
        }
        return count;
    }

    private static RevCommit mergeBase(RevWalk walk, RevCommit a, RevCommit b) throws IOException {
        walk.reset();
        walk.sort(RevSort.NONE);
        walk.setRevFilter(RevFilter.MERGE_BASE);
        walk.markStart(a);
        walk.markStart(b);
        return walk.next();
    }

    private static int changedFiles(Repository repository, RevCommit base, RevCommit source) throws IOException {
        try (DiffFormatter diff = new DiffFormatter(DisabledOutputStream.INSTANCE)) {
            diff.setRepository(repository);
            diff.setDetectRenames(true);
            return diff.scan(base == null ? null : base.getTree(), source.getTree()).size();
        }
    }

    /**
     * Files changed differently on HEAD and on {@code ref} since their common commit (sorted): the ones a
     * merge may stop on. Files changed the same way on both sides are left out. Empty without HEAD or
     * common commit.
     *
     * @throws GitException if {@code ref} is not a commit
     */
    public static List<String> changedOnBothSides(Repository repository, String ref) throws IOException {
        ObjectId sourceId = repository.resolve(ref + "^{commit}");
        if (sourceId == null) {
            throw new GitException("Not a branch or commit: " + ref);
        }
        ObjectId headId = repository.resolve(Constants.HEAD + "^{commit}");
        if (headId == null) {
            return List.of();
        }
        try (RevWalk walk = new RevWalk(repository)) {
            RevCommit head = walk.parseCommit(headId);
            RevCommit source = walk.parseCommit(sourceId);
            RevCommit base = mergeBase(walk, head, source);
            if (base == null) {
                return List.of();
            }
            Map<String, ObjectId> ours = changes(repository, base, head);
            Map<String, ObjectId> theirs = changes(repository, base, source);
            List<String> result = new ArrayList<>();
            for (Map.Entry<String, ObjectId> entry : ours.entrySet()) {
                ObjectId other = theirs.get(entry.getKey());
                if (theirs.containsKey(entry.getKey()) && !Objects.equals(entry.getValue(), other)) {
                    result.add(entry.getKey());
                }
            }
            result.sort(null);
            return result;
        }
    }

    /** Changed paths (old and new names) with their new content id ({@code null} when deleted). */
    private static Map<String, ObjectId> changes(Repository repository, RevCommit from, RevCommit to)
            throws IOException {
        Map<String, ObjectId> result = new HashMap<>();
        try (DiffFormatter diff = new DiffFormatter(DisabledOutputStream.INSTANCE)) {
            diff.setRepository(repository);
            for (DiffEntry entry : diff.scan(from.getTree(), to.getTree())) {
                ObjectId id = entry.getChangeType() == DiffEntry.ChangeType.DELETE ? null
                        : entry.getNewId().toObjectId();
                if (!DiffEntry.DEV_NULL.equals(entry.getOldPath())) {
                    result.put(entry.getOldPath(), id);
                }
                if (!DiffEntry.DEV_NULL.equals(entry.getNewPath())) {
                    result.put(entry.getNewPath(), id);
                }
            }
        }
        return result;
    }

    private static CommitInfo info(RevCommit commit) {
        List<String> parents = new ArrayList<>(commit.getParentCount());
        for (RevCommit parent : commit.getParents()) {
            parents.add(parent.getName());
        }
        return new CommitInfo(commit.getName(), parents, commit.getShortMessage(),
                commit.getAuthorIdent().getName(), commit.getAuthorIdent().getWhenAsInstant(), List.of(), false);
    }
}
