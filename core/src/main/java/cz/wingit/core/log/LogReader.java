package cz.wingit.core.log;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.RefDatabase;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevObject;
import org.eclipse.jgit.revwalk.RevSort;
import org.eclipse.jgit.revwalk.RevWalk;

/**
 * Reads the newest commits reachable from HEAD with JGit, stopping after {@code limit}
 * (the whole history is never loaded), together with the branch/tag labels of each commit.
 * <p>
 * Commits are ordered by commit time, newest first (streamed; a topological sort would have to
 * read the whole history first). Commits not yet on any remote-tracking branch are marked as unpushed.
 */
public final class LogReader {

    private static final Comparator<RefLabel> LABEL_ORDER = Comparator
            .comparing(RefLabel::kind)
            .thenComparing(label -> !label.current())
            .thenComparing(RefLabel::name, String.CASE_INSENSITIVE_ORDER);

    private LogReader() {
    }

    /** Newest first; empty for a repository without commits. */
    public static List<CommitInfo> recent(Repository repository, int limit) throws IOException {
        if (limit <= 0) {
            throw new IllegalArgumentException("limit must be positive: " + limit);
        }
        ObjectId head = repository.resolve(Constants.HEAD);
        if (head == null) {
            return List.of();
        }
        Map<ObjectId, List<RefLabel>> labels = labels(repository);
        Set<ObjectId> unpushed = unpushed(repository, head, limit);
        List<CommitInfo> commits = new ArrayList<>(limit);
        try (RevWalk walk = new RevWalk(repository)) {
            walk.sort(RevSort.COMMIT_TIME_DESC);
            walk.markStart(walk.parseCommit(head));
            for (RevCommit commit : walk) {
                if (commits.size() == limit) {
                    break;
                }
                List<String> parents = new ArrayList<>(commit.getParentCount());
                for (RevCommit parent : commit.getParents()) {
                    parents.add(parent.getName());
                }
                commits.add(new CommitInfo(
                        commit.getName(),
                        parents,
                        commit.getShortMessage(),
                        commit.getAuthorIdent().getName(),
                        commit.getAuthorIdent().getWhenAsInstant(),
                        labels.getOrDefault(commit.getId(), List.of()),
                        unpushed.contains(commit.getId())));
            }
        }
        return commits;
    }

    /**
     * Full messages (trimmed) of the newest non-merge commits on HEAD, newest first, without
     * duplicates; at most {@code limit} messages, looking at no more than {@code 20 * limit} commits.
     */
    public static List<String> recentMessages(Repository repository, int limit) throws IOException {
        ObjectId head = repository.resolve(Constants.HEAD);
        if (head == null || limit <= 0) {
            return List.of();
        }
        Set<String> messages = new LinkedHashSet<>();
        int scanned = 0;
        try (RevWalk walk = new RevWalk(repository)) {
            walk.sort(RevSort.COMMIT_TIME_DESC);
            walk.markStart(walk.parseCommit(head));
            for (RevCommit commit : walk) {
                if (messages.size() == limit || ++scanned > 20 * limit) {
                    break;
                }
                String message = commit.getFullMessage().strip();
                if (commit.getParentCount() <= 1 && !message.isEmpty()) {
                    messages.add(message);
                }
            }
        }
        return List.copyOf(messages);
    }

    /**
     * Number of commits on HEAD that are on no remote-tracking branch yet, counted up to {@code max}.
     * 0 without commits or without any remote-tracking branch (same rule as {@link CommitInfo#unpushed()}).
     */
    public static int unpushedCount(Repository repository, int max) throws IOException {
        ObjectId head = repository.resolve(Constants.HEAD);
        return head == null ? 0 : unpushed(repository, head, max).size();
    }

    /**
     * Commits reachable from HEAD but from no remote-tracking branch ({@code HEAD --not --remotes}),
     * at most {@code limit}. The walk stops where the remote branches join the history, so it only
     * visits the local commits plus a small boundary, not the whole history.
     */
    private static Set<ObjectId> unpushed(Repository repository, ObjectId head, int limit) throws IOException {
        List<Ref> remotes = repository.getRefDatabase().getRefsByPrefix(Constants.R_REMOTES);
        Set<ObjectId> result = new HashSet<>();
        try (RevWalk walk = new RevWalk(repository)) {
            boolean anyRemote = false;
            for (Ref ref : remotes) {
                if (ref.isSymbolic() || ref.getObjectId() == null) {
                    continue;
                }
                RevObject target = walk.peel(walk.parseAny(ref.getObjectId()));
                if (target instanceof RevCommit remoteCommit) {
                    walk.markUninteresting(remoteCommit);
                    anyRemote = true;
                }
            }
            if (!anyRemote) {
                return result;
            }
            walk.markStart(walk.parseCommit(head));
            for (RevCommit commit : walk) {
                if (result.size() == limit) {
                    break;
                }
                result.add(commit.toObjectId());
            }
        }
        return result;
    }

    private static Map<ObjectId, List<RefLabel>> labels(Repository repository) throws IOException {
        Map<ObjectId, List<RefLabel>> labels = new HashMap<>();
        RefDatabase refs = repository.getRefDatabase();
        String head = repository.getFullBranch();

        if (head != null && ObjectId.isId(head)) {
            add(labels, ObjectId.fromString(head), new RefLabel(Constants.HEAD, RefLabel.Kind.DETACHED_HEAD, true));
        }
        for (Ref ref : refs.getRefsByPrefix(Constants.R_HEADS)) {
            add(labels, ref.getObjectId(), new RefLabel(Repository.shortenRefName(ref.getName()),
                    RefLabel.Kind.LOCAL_BRANCH, ref.getName().equals(head)));
        }
        for (Ref ref : refs.getRefsByPrefix(Constants.R_REMOTES)) {
            if (!ref.isSymbolic()) {
                add(labels, ref.getObjectId(), new RefLabel(Repository.shortenRefName(ref.getName()),
                        RefLabel.Kind.REMOTE_BRANCH, false));
            }
        }
        for (Ref ref : refs.getRefsByPrefix(Constants.R_TAGS)) {
            Ref peeled = refs.peel(ref);
            ObjectId target = peeled.getPeeledObjectId() != null ? peeled.getPeeledObjectId() : ref.getObjectId();
            add(labels, target, new RefLabel(Repository.shortenRefName(ref.getName()), RefLabel.Kind.TAG, false));
        }
        labels.values().forEach(list -> list.sort(LABEL_ORDER));
        return labels;
    }

    private static void add(Map<ObjectId, List<RefLabel>> labels, ObjectId target, RefLabel label) {
        if (target != null) {
            labels.computeIfAbsent(target, id -> new ArrayList<>()).add(label);
        }
    }
}
