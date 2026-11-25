package cz.wingit.core.log;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevSort;
import org.eclipse.jgit.revwalk.RevWalk;

import cz.wingit.core.GitException;

/**
 * The whole history of one branch (or tag, or commit), read in portions with {@link #next(int)} so that
 * even a very long history can be shown while it is being read. Commits come newest first by commit
 * time, as in the main window's history.
 * <p>
 * For a local branch with an upstream, the upstream's commits that the branch does not have yet are
 * included and marked unpulled. Commits reachable from the start but from no remote-tracking branch are
 * marked unpushed; those are found when the reader is opened (the walk stops where the remote branches
 * join the history).
 * <p>
 * Use from one thread at a time; {@link #close()} releases the repository.
 */
public final class HistoryReader implements AutoCloseable {

    private final Repository repository;
    private final RevWalk walk;
    private final Iterator<RevCommit> commits;
    private final Map<ObjectId, List<RefLabel>> labels;
    private final Set<ObjectId> unpushed;
    private final Set<ObjectId> unpulled;
    /** Author names are repeated many times; one String instance per name saves memory. */
    private final Map<String, String> names = new HashMap<>();
    private int count;

    private HistoryReader(Repository repository, RevWalk walk, Map<ObjectId, List<RefLabel>> labels,
                          Set<ObjectId> unpushed, Set<ObjectId> unpulled) {
        this.repository = repository;
        this.walk = walk;
        this.commits = walk.iterator();
        this.labels = labels;
        this.unpushed = unpushed;
        this.unpulled = unpulled;
    }

    /**
     * Starts reading the history of {@code revision}: {@code HEAD}, a full ref name ({@code refs/heads/main},
     * {@code refs/remotes/origin/main}, {@code refs/tags/v1.0}) or a commit id. {@code HEAD} without
     * commits gives an empty history. Takes over {@code repository}: it is closed with the reader, also
     * when opening fails.
     *
     * @throws IOException if the repository cannot be read or {@code revision} is not a commit
     */
    public static HistoryReader open(Repository repository, String revision) throws IOException {
        RevWalk walk = null;
        try {
            ObjectId start = repository.resolve(revision + "^{commit}");
            walk = new RevWalk(repository);
            walk.sort(RevSort.COMMIT_TIME_DESC);
            if (start == null) {
                if (!Constants.HEAD.equals(revision)) {
                    throw new IOException("Unknown revision " + revision);
                }
                return new HistoryReader(repository, walk, Map.of(), Set.of(), Set.of()); // no commits yet
            }
            walk.markStart(walk.parseCommit(start));
            ObjectId upstream = upstreamOf(repository, revision);
            Set<ObjectId> unpulled = upstream == null ? Set.of()
                    : LogReader.unpulled(repository, start, upstream, Integer.MAX_VALUE);
            if (!unpulled.isEmpty()) {
                walk.markStart(walk.parseCommit(upstream));
            }
            return new HistoryReader(repository, walk, LogReader.labels(repository),
                    LogReader.unpushed(repository, start, Integer.MAX_VALUE), unpulled);
        } catch (IOException | RuntimeException e) {
            if (walk != null) {
                walk.close();
            }
            repository.close();
            throw e;
        }
    }

    /**
     * The next at most {@code max} commits, older than all returned before; empty once the whole history
     * has been read.
     *
     * @throws GitException if the repository cannot be read
     */
    public List<CommitInfo> next(int max) {
        if (max <= 0) {
            throw new IllegalArgumentException("max must be positive: " + max);
        }
        List<CommitInfo> portion = new ArrayList<>(Math.min(max, 4096));
        try {
            while (portion.size() < max && commits.hasNext()) {
                portion.add(info(commits.next()));
            }
        } catch (RuntimeException e) {
            // JGit's walk iterator wraps read errors in unchecked exceptions
            throw new GitException("Cannot read history: " + e.getMessage(), e);
        }
        count += portion.size();
        return portion;
    }

    /** Number of commits returned so far. */
    public int count() {
        return count;
    }

    @Override
    public void close() {
        walk.close();
        repository.close();
    }

    private CommitInfo info(RevCommit commit) {
        List<String> parents = new ArrayList<>(commit.getParentCount());
        for (RevCommit parent : commit.getParents()) {
            parents.add(parent.getName());
        }
        String author = commit.getAuthorIdent().getName();
        return new CommitInfo(
                commit.getName(),
                parents,
                commit.getShortMessage(),
                names.computeIfAbsent(author, name -> name),
                commit.getAuthorIdent().getWhenAsInstant(),
                labels.getOrDefault(commit.getId(), List.of()),
                unpushed.contains(commit.getId()),
                unpulled.contains(commit.getId()));
    }

    /** Upstream commit of {@code revision} if it is (or HEAD points to) a local branch with an upstream. */
    private static ObjectId upstreamOf(Repository repository, String revision) throws IOException {
        Ref ref = repository.exactRef(revision);
        if (ref == null) {
            return null;
        }
        String name = ref.getTarget().getName(); // HEAD -> refs/heads/<current branch>
        if (!name.startsWith(Constants.R_HEADS)) {
            return null;
        }
        return LogReader.upstream(repository, name.substring(Constants.R_HEADS.length()));
    }
}
