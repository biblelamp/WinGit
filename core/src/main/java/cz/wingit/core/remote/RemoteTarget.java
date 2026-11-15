package cz.wingit.core.remote;

import java.io.IOException;
import java.util.Set;

import org.eclipse.jgit.lib.ConfigConstants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.StoredConfig;

import cz.wingit.core.GitException;

/**
 * Where the current branch is pushed to or pulled from, read from the repository configuration.
 *
 * @param branch    short name of the checked-out branch
 * @param remote    remote name, e.g. {@code origin}
 * @param remoteRef full name of the upstream branch on the remote (e.g. {@code refs/heads/main}),
 *                  or {@code null} if the branch has no upstream yet
 * @param url       URL Git talks to for this operation
 */
public record RemoteTarget(String branch, String remote, String remoteRef, String url) {

    private static final String DEFAULT_REMOTE = "origin";

    public boolean hasUpstream() {
        return remoteRef != null;
    }

    /**
     * Target for pushing the current branch: its upstream, or (without one) {@code origin} or the
     * only remote, to a branch of the same name.
     *
     * @throws GitException if HEAD is detached or no suitable remote exists
     */
    public static RemoteTarget forPush(Repository repository) {
        return resolve(repository, true);
    }

    /**
     * Target for pulling into the current branch: its upstream.
     *
     * @throws GitException if HEAD is detached or the branch has no upstream
     */
    public static RemoteTarget forPull(Repository repository) {
        RemoteTarget target = resolve(repository, false);
        if (!target.hasUpstream()) {
            throw new GitException("Branch '" + target.branch() + "' does not track a remote branch yet. "
                    + "Push it first to set its upstream.");
        }
        return target;
    }

    private static RemoteTarget resolve(Repository repository, boolean push) {
        String branch = currentBranch(repository);
        StoredConfig config = repository.getConfig();
        String remote = config.getString(ConfigConstants.CONFIG_BRANCH_SECTION, branch,
                ConfigConstants.CONFIG_KEY_REMOTE);
        String merge = config.getString(ConfigConstants.CONFIG_BRANCH_SECTION, branch,
                ConfigConstants.CONFIG_KEY_MERGE);
        boolean upstream = remote != null && merge != null && !".".equals(remote);
        if (!upstream) {
            if (!push) {
                return new RemoteTarget(branch, remote, null, null);
            }
            remote = defaultRemote(config.getSubsections(ConfigConstants.CONFIG_REMOTE_SECTION));
            merge = null;
        }
        String url = push ? config.getString(ConfigConstants.CONFIG_REMOTE_SECTION, remote, "pushurl") : null;
        if (url == null) {
            url = config.getString(ConfigConstants.CONFIG_REMOTE_SECTION, remote, ConfigConstants.CONFIG_KEY_URL);
        }
        if (url == null) {
            throw new GitException("Remote '" + remote + "' has no URL configured.");
        }
        return new RemoteTarget(branch, remote, merge, url);
    }

    private static String currentBranch(Repository repository) {
        String branch;
        try {
            branch = repository.getBranch();
        } catch (IOException e) {
            throw new GitException("Cannot read the current branch: " + e.getMessage(), e);
        }
        if (branch == null || ObjectId.isId(branch)) {
            throw new GitException("No branch is checked out (HEAD is detached).");
        }
        return branch;
    }

    private static String defaultRemote(Set<String> remotes) {
        if (remotes.contains(DEFAULT_REMOTE)) {
            return DEFAULT_REMOTE;
        }
        if (remotes.size() == 1) {
            return remotes.iterator().next();
        }
        if (remotes.isEmpty()) {
            throw new GitException("The repository has no remote to push to.");
        }
        throw new GitException("The branch has no upstream and there are several remotes; "
                + "set the upstream with 'git push -u <remote> <branch>' first.");
    }
}
