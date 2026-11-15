package cz.wingit.core;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

import cz.wingit.core.diff.FileDiff;
import cz.wingit.core.log.CommitInfo;
import cz.wingit.core.refs.RepositoryRefs;
import cz.wingit.core.status.FileChange;
import cz.wingit.core.status.RepositoryStatus;
import cz.wingit.core.watch.RepositoryWatch;

/**
 * Git operations used by the UI. Implementations block; never call them on the EDT.
 */
public interface GitService {

    /** {@code true} if {@code dir} is the root of a Git working tree. */
    boolean isWorkTree(Path dir);

    /** The working tree root containing {@code dir} ({@code dir} itself or a parent), if any. */
    Optional<Path> findWorkTreeRoot(Path dir);

    /**
     * Short name of the checked-out branch, or an abbreviated commit id when HEAD is detached.
     * Empty if the repository cannot be read.
     */
    Optional<String> currentBranch(Path workTree);

    /**
     * Working tree status (staged, unstaged, untracked and conflicting files; ignored files excluded).
     *
     * @throws GitException if the repository cannot be read
     */
    RepositoryStatus status(Path workTree);

    /**
     * Both versions of a changed file and their aligned line diff: index vs working tree for
     * unstaged changes, HEAD vs index for staged-only changes, HEAD vs working tree for conflicts.
     *
     * @throws GitException if the repository cannot be read
     */
    FileDiff fileDiff(Path workTree, FileChange change);

    /**
     * Local and remote branches (with upstream and ahead/behind counts), tags and stashes.
     *
     * @throws GitException if the repository cannot be read
     */
    RepositoryRefs refs(Path workTree);

    /**
     * The newest {@code limit} commits reachable from HEAD, newest first (empty if there are none).
     *
     * @throws GitException if the repository cannot be read
     */
    List<CommitInfo> recentCommits(Path workTree, int limit);

    /**
     * Watches the working tree for changes that can affect status or the current branch
     * (debounced; Git-ignored paths and .git internals such as objects are skipped).
     * {@code onChange} runs on a background thread.
     *
     * @throws GitException if the watch cannot be set up
     */
    RepositoryWatch watch(Path workTree, Runnable onChange);

    /**
     * Checks out the local branch {@code branch} ({@code git switch}). Fails, leaving everything
     * as it was, if local changes would be overwritten.
     *
     * @throws GitCommandException if Git refuses the switch
     */
    void switchBranch(Path workTree, String branch);

    /**
     * Creates the local branch {@code branch} tracking {@code remote/branch} and checks it out.
     *
     * @throws GitCommandException if the branch already exists or Git refuses the switch
     */
    void switchToNewTrackingBranch(Path workTree, String remote, String branch);

    /** Runs {@code git init} in an existing directory. */
    void init(Path dir);

    /**
     * Clones {@code url} into {@code target}. Progress lines from Git are passed to {@code progress}
     * (called on the worker thread). If the clone fails or is cancelled, a {@code target} directory
     * created by this call is removed again.
     *
     * @throws GitCancelledException if the calling thread is interrupted
     */
    void clone(String url, Path target, Consumer<String> progress);
}
