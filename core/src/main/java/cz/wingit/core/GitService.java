package cz.wingit.core;

import java.nio.file.Path;
import java.util.Optional;
import java.util.function.Consumer;

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
