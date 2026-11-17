package cz.wingit.core.refs;

import java.util.List;
import java.util.Map;

/**
 * Branches, tags and stashes of a repository.
 *
 * @param detachedAt  abbreviated commit id when HEAD is detached, otherwise {@code null}
 * @param tagSubjects first line of the message of the commit each tag points to, by tag name;
 *                    missing for tags that do not point to a commit
 */
public record RepositoryRefs(List<LocalBranch> localBranches, String detachedAt,
                             List<RemoteBranches> remotes, List<String> tags, List<Stash> stashes,
                             Map<String, String> tagSubjects) {

    public RepositoryRefs {
        localBranches = List.copyOf(localBranches);
        remotes = List.copyOf(remotes);
        tags = List.copyOf(tags);
        stashes = List.copyOf(stashes);
        tagSubjects = Map.copyOf(tagSubjects);
    }

    /** Without tag commit subjects. */
    public RepositoryRefs(List<LocalBranch> localBranches, String detachedAt,
                          List<RemoteBranches> remotes, List<String> tags, List<Stash> stashes) {
        this(localBranches, detachedAt, remotes, tags, stashes, Map.of());
    }
}
