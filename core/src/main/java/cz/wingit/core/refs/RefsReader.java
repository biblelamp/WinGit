package cz.wingit.core.refs;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.BranchTrackingStatus;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.RefDatabase;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;

/**
 * Reads {@link RepositoryRefs} with JGit. Names are sorted case-insensitively.
 */
public final class RefsReader {

    private static final Comparator<String> BY_NAME =
            String.CASE_INSENSITIVE_ORDER.thenComparing(Comparator.naturalOrder());
    private static final int ABBREV_LENGTH = 7;

    private RefsReader() {
    }

    public static RepositoryRefs read(Git git) throws IOException, GitAPIException {
        Repository repository = git.getRepository();
        RefDatabase refs = repository.getRefDatabase();
        String head = repository.getFullBranch();

        List<LocalBranch> local = new ArrayList<>();
        for (Ref ref : refs.getRefsByPrefix(Constants.R_HEADS)) {
            String name = Repository.shortenRefName(ref.getName());
            BranchTrackingStatus tracking = BranchTrackingStatus.of(repository, name);
            local.add(new LocalBranch(
                    name,
                    tracking == null ? null : Repository.shortenRefName(tracking.getRemoteTrackingBranch()),
                    tracking == null ? 0 : tracking.getAheadCount(),
                    tracking == null ? 0 : tracking.getBehindCount(),
                    ref.getName().equals(head)));
        }
        local.sort(Comparator.comparing(LocalBranch::name, BY_NAME));

        String detachedAt = head != null && ObjectId.isId(head) ? head.substring(0, ABBREV_LENGTH) : null;

        return new RepositoryRefs(local, detachedAt, remoteBranches(repository, refs), tags(refs),
                stashes(git));
    }

    private static List<RemoteBranches> remoteBranches(Repository repository, RefDatabase refs) throws IOException {
        Map<String, List<String>> byRemote = new TreeMap<>(BY_NAME);
        for (String remote : repository.getRemoteNames()) {
            byRemote.put(remote, new ArrayList<>());
        }
        for (Ref ref : refs.getRefsByPrefix(Constants.R_REMOTES)) {
            if (ref.isSymbolic()) {
                continue; // e.g. origin/HEAD -> origin/main
            }
            String rest = ref.getName().substring(Constants.R_REMOTES.length());
            String remote = remoteOf(rest, byRemote.keySet());
            if (remote == null) {
                continue;
            }
            String branch = rest.substring(remote.length() + 1);
            if (!branch.equals(Constants.HEAD)) {
                byRemote.computeIfAbsent(remote, r -> new ArrayList<>()).add(branch);
            }
        }
        List<RemoteBranches> result = new ArrayList<>();
        byRemote.forEach((remote, branches) -> {
            branches.sort(BY_NAME);
            result.add(new RemoteBranches(remote, branches));
        });
        return result;
    }

    /** The configured remote that prefixes {@code rest} (longest match, remote names may contain '/'). */
    private static String remoteOf(String rest, Iterable<String> knownRemotes) {
        String best = null;
        for (String remote : knownRemotes) {
            if (rest.startsWith(remote + "/") && (best == null || remote.length() > best.length())) {
                best = remote;
            }
        }
        if (best != null) {
            return best;
        }
        int slash = rest.indexOf('/');
        return slash > 0 ? rest.substring(0, slash) : null;
    }

    private static List<String> tags(RefDatabase refs) throws IOException {
        List<String> tags = new ArrayList<>();
        for (Ref ref : refs.getRefsByPrefix(Constants.R_TAGS)) {
            tags.add(Repository.shortenRefName(ref.getName()));
        }
        tags.sort(BY_NAME);
        return tags;
    }

    private static List<Stash> stashes(Git git) throws GitAPIException {
        List<Stash> stashes = new ArrayList<>();
        int index = 0;
        for (RevCommit commit : git.stashList().call()) {
            stashes.add(new Stash(index++, commit.getShortMessage()));
        }
        return stashes;
    }
}
