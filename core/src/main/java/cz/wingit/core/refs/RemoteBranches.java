package cz.wingit.core.refs;

import java.util.List;

/**
 * Branches of one remote, as last fetched ({@code refs/remotes/<remote>/...}), without {@code HEAD}.
 *
 * @param branches branch names without the remote prefix, sorted
 */
public record RemoteBranches(String remote, List<String> branches) {

    public RemoteBranches {
        branches = List.copyOf(branches);
    }
}
