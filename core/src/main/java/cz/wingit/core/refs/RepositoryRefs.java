package cz.wingit.core.refs;

import java.util.List;

/**
 * Branches, tags and stashes of a repository.
 *
 * @param detachedAt abbreviated commit id when HEAD is detached, otherwise {@code null}
 */
public record RepositoryRefs(List<LocalBranch> localBranches, String detachedAt,
                             List<RemoteBranches> remotes, List<String> tags, List<Stash> stashes) {

    public RepositoryRefs {
        localBranches = List.copyOf(localBranches);
        remotes = List.copyOf(remotes);
        tags = List.copyOf(tags);
        stashes = List.copyOf(stashes);
    }
}
