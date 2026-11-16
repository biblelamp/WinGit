package cz.wingit.core;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

import cz.wingit.core.diff.FileDiff;
import cz.wingit.core.log.CommitInfo;
import cz.wingit.core.merge.MergeResult;
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
     * Merges {@code ref} (a local branch such as {@code feature} or a remote one such as
     * {@code origin/feature}) into the current branch, without opening an editor. Fast-forwards when
     * possible unless {@code noFastForward}. On conflicts the repository stays in the merge, to be
     * resolved and committed, or aborted with {@link #abortMerge}. The outcome is determined from the
     * repository, not from Git's (possibly translated) messages.
     *
     * @throws GitCommandException if Git refuses to start (e.g. local changes would be overwritten)
     */
    MergeResult merge(Path workTree, String ref, boolean noFastForward);

    /** {@code true} while a merge stopped with conflicts (or before its commit) is in progress. */
    boolean mergeInProgress(Path workTree);

    /** The message Git prepared for the commit of the merge in progress, if any. */
    Optional<String> mergeMessage(Path workTree);

    /**
     * Abandons the merge in progress ({@code git merge --abort}) and restores the state before it.
     *
     * @throws GitCommandException if there is no merge to abort or Git cannot restore the state
     */
    void abortMerge(Path workTree);

    /**
     * Creates the local branch {@code name} at the current commit and, if {@code checkout}, switches
     * to it (local changes are kept). Check the name with {@code BranchNames} first.
     *
     * @throws GitCommandException if Git refuses (e.g. the branch exists or there is no commit yet)
     */
    void createBranch(Path workTree, String name, boolean checkout);

    /**
     * Renames the local branch {@code name} to {@code newName} ({@code git branch -m}); also works
     * for the checked-out branch and for a change of letter case only. Its upstream setting is kept;
     * the branch on the server is not renamed.
     *
     * @throws GitCommandException if Git refuses (e.g. a branch {@code newName} exists)
     */
    void renameBranch(Path workTree, String name, String newName);

    /**
     * Deletes the local branch {@code name} ({@code git branch -d}, or {@code -D} with {@code force}).
     * The checked-out branch cannot be deleted.
     *
     * @throws GitBranchNotMergedException if not forced and the branch has commits that are not merged
     * @throws GitCommandException         if Git refuses otherwise (e.g. it is checked out)
     */
    void deleteBranch(Path workTree, String name, boolean force);

    /**
     * Creates the annotated tag {@code name} with {@code message} on {@code commit}. Check the name
     * with {@code TagNames} first.
     *
     * @param commit  full or abbreviated commit id, or {@code null} for the current commit (HEAD)
     * @param message tag message; blank uses the tag name
     * @throws GitCommandException if Git refuses (e.g. the tag exists, no commit yet, no identity)
     */
    void createTag(Path workTree, String name, String commit, String message);

    /**
     * Deletes the local tag {@code name}; the tag on the server is not touched.
     *
     * @throws GitCommandException if Git refuses (e.g. no such tag)
     */
    void deleteTag(Path workTree, String name);

    /**
     * The remote that tags are pushed to: the current branch's remote, else {@code origin} or the only
     * remote. Empty if the repository has none (or several and none is obvious).
     */
    Optional<String> defaultRemote(Path workTree);

    /**
     * Pushes the tag {@code name} to {@code remote}.
     *
     * @param credentials as for {@link #push}
     * @throws GitAuthenticationException if the remote needs (other) credentials
     * @throws GitCommandException        if the server refuses (e.g. a different tag of that name exists)
     */
    void pushTag(Path workTree, String remote, String name, Credentials credentials, Consumer<String> progress);

    /**
     * Deletes the tag {@code name} on the server of {@code remote}; the local tag is not touched.
     *
     * @param credentials as for {@link #push}
     * @throws GitAuthenticationException if the remote needs (other) credentials
     * @throws GitCommandException        if the server refuses
     */
    void deleteRemoteTag(Path workTree, String remote, String name, Credentials credentials,
                         Consumer<String> progress);

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
     * Stages the working tree state of the given files (new, modified and deleted alike);
     * files without unstaged changes are skipped.
     *
     * @throws GitCommandException if Git refuses
     */
    void stage(Path workTree, List<FileChange> changes);

    /**
     * Removes the staged changes of the given files from the index; their working tree is not touched.
     * Works in a repository without commits too. Files without staged changes are skipped.
     *
     * @throws GitCommandException if Git refuses
     */
    void unstage(Path workTree, List<FileChange> changes);

    /**
     * Throws away all changes of the given files (staged and unstaged), so they are as in HEAD again:
     * modified and deleted files are restored, files that are new (untracked or only added to the
     * index) are deleted from disk. Cannot be undone.
     *
     * @throws GitCommandException if Git refuses
     */
    void discard(Path workTree, List<FileChange> changes);

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
     * {@code origin} (or the only remote) under the same name and starts tracking it. Annotated tags
     * on the pushed commits that the server does not have yet go along ({@code --follow-tags}).
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
     * Deletes branch {@code branch} on the server of {@code remote} ({@code git push --delete}); the
     * local remote-tracking branch goes away with it. Local branches are not touched.
     *
     * @param credentials as for {@link #push}
     * @throws GitAuthenticationException if the remote needs (other) credentials
     * @throws GitCommandException        if the server refuses (e.g. protected or unknown branch)
     */
    void deleteRemoteBranch(Path workTree, String remote, String branch, Credentials credentials,
                            Consumer<String> progress);

    /**
     * Renames branch {@code name} on the server of {@code remote} to {@code newName}: one atomic push
     * creates {@code newName} at the commit of {@code remote/name} as last fetched and deletes
     * {@code name}. Nothing changes if the server branch moved since the last fetch (its commits
     * would be lost) or {@code newName} already exists there. Local branches that tracked
     * {@code remote/name} track {@code remote/newName} afterwards; their own names are kept.
     *
     * @param credentials as for {@link #push}
     * @throws GitAuthenticationException if the remote needs (other) credentials
     * @throws GitCommandException        if the server refuses (moved, exists, protected, no atomic push)
     */
    void renameRemoteBranch(Path workTree, String remote, String name, String newName, Credentials credentials,
                            Consumer<String> progress);

    /**
     * Hands credentials that just worked to the configured Git credential helper (normally Git
     * Credential Manager) via {@code git credential approve}. WinGit itself stores no password; without
     * a helper nothing is saved.
     * <p>
     * The user name is also set as {@code credential.<scheme://host>.username} in the repository's
     * local config, so the helper later knows which saved account to use without asking (GCM cannot
     * choose between several saved accounts of one host on its own).
     */
    void storeCredentials(Path workTree, String url, Credentials credentials);
}
