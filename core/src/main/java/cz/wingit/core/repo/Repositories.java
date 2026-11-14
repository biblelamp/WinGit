package cz.wingit.core.repo;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;

import org.eclipse.jgit.lib.RepositoryCache;
import org.eclipse.jgit.util.FS;

/**
 * Helpers for locating Git repositories on disk.
 */
public final class Repositories {

    private Repositories() {
    }

    /**
     * Returns {@code true} if {@code workTree} is the root of a non-bare Git working tree,
     * i.e. it contains a valid {@code .git} directory or a {@code .git} file (worktree / submodule).
     */
    public static boolean isWorkTree(Path workTree) {
        Objects.requireNonNull(workTree, "workTree");
        if (!Files.isDirectory(workTree)) {
            return false;
        }
        Path dotGit = workTree.resolve(".git");
        if (Files.isRegularFile(dotGit)) {
            return true;
        }
        return Files.isDirectory(dotGit)
                && RepositoryCache.FileKey.isGitRepository(dotGit.toFile(), FS.DETECTED);
    }

    /** The nearest working tree root at {@code dir} or above it, if any. */
    public static Optional<Path> findWorkTreeRoot(Path dir) {
        Objects.requireNonNull(dir, "dir");
        for (Path current = dir.toAbsolutePath().normalize(); current != null; current = current.getParent()) {
            if (isWorkTree(current)) {
                return Optional.of(current);
            }
        }
        return Optional.empty();
    }
}
