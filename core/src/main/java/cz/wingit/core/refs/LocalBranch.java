package cz.wingit.core.refs;

/**
 * A local branch.
 *
 * @param name     short name, e.g. {@code feature/login}
 * @param upstream short name of the tracked remote branch (e.g. {@code origin/main}), or {@code null}
 * @param ahead    commits on this branch not in the upstream (0 without upstream)
 * @param behind   commits in the upstream not on this branch (0 without upstream)
 * @param current  whether this branch is checked out
 */
public record LocalBranch(String name, String upstream, int ahead, int behind, boolean current) {
}
