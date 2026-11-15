package cz.wingit.core;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

import cz.wingit.core.diff.FileDiff;
import cz.wingit.core.log.CommitInfo;
import cz.wingit.core.refs.RepositoryRefs;
import cz.wingit.core.remote.Credentials;
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
     * Full messages of the newest commits on HEAD (merges skipped, duplicates removed), newest first,
     * at most {@code limit}; e.g. to reuse one in the commit dialog.
     *
     * @throws GitException if the repository cannot be read
     */
    List<String> recentMessages(Path workTree, int limit);

    /**
     * How many commits of the checked-out branch are not pushed yet (on no remote-tracking branch),
     * counted up to {@code max}. 0 for a repository without commits or remote-tracking branches.
     *
     * @throws GitException if the repository cannot be read
     */
    int unpushedCount(Path workTree, int max);

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

    /**
     * Commits the given changed files with {@code message}. Their working tree state is staged first
     * (new, modified and deleted files alike); other staged changes are not committed. While a merge
     * (or cherry-pick, revert, ...) is in progress the whole index is committed, as Git allows no
     * partial commit then.
     *
     * @param changes files from {@link #status}, at least one
     * @throws GitCommandException if Git refuses the commit (e.g. no user identity configured)
     */
    default void commit(Path workTree, List<FileChange> changes, String message) {
        commit(workTree, changes, message, null);
    }

    /**
     * As {@link #commit(Path, List, String)}, but with the given author and committer date
     * (e.g. to record work done earlier), in the time zone of this computer.
     *
     * @param date commit date, or {@code null} for now
     */
    void commit(Path workTree, List<FileChange> changes, String message, Instant date);

    /**
     * Pushes the current branch to its upstream. A branch without upstream is pushed to
     * {@code origin} (or the only remote) under the same name and starts tracking it.
     *
     * @param credentials user name and password entered by the user for this attempt, or
     *                    {@code null} to use saved credentials only (Git Credential Manager, SSH agent);
     *                    the credential manager opens no window of its own, missing credentials end in
     *                    {@link GitAuthenticationException}
     * @param progress    receives Git progress lines (called on the worker thread)
     * @throws GitAuthenticationException if the remote needs (other) credentials
     * @throws GitCancelledException      if the calling thread is interrupted
     */
    void push(Path workTree, Credentials credentials, Consumer<String> progress);

    /**
     * Pulls the upstream of the current branch into it (merge, or rebase if configured by
     * {@code pull.rebase}); no editor is opened for merge commits.
     *
     * @param credentials as for {@link #push}
     * @throws GitAuthenticationException if the remote needs (other) credentials
     * @throws GitCancelledException      if the calling thread is interrupted
     */
    void pull(Path workTree, Credentials credentials, Consumer<String> progress);

    /**
     * Hands credentials that just worked to the configured Git credential helper (normally Git
     * Credential Manager) via {@code git credential approve}. WinGit itself stores nothing; without
     * a helper this does nothing.
     */
    void storeCredentials(Path workTree, String url, Credentials credentials);
}
