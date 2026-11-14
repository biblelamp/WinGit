package cz.wingit.core.watch;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;

import org.eclipse.jgit.api.Git;

/**
 * Creates watches that skip Git-ignored paths.
 */
public final class RepositoryWatches {

    private RepositoryWatches() {
    }

    /**
     * Watches {@code workTree}; {@code onChange} runs on a background thread after relevant,
     * non-ignored changes (debounced).
     */
    public static RepositoryWatch start(Path workTree, Duration debounce, Runnable onChange) throws IOException {
        Git git = Git.open(workTree.toFile());
        try {
            RepositoryWatcher watcher = RepositoryWatcher.start(
                    workTree, new GitIgnoreChecker(git.getRepository()), debounce, onChange);
            return () -> {
                watcher.close(); // waits for a running ignore check before the repository is closed
                git.close();
            };
        } catch (IOException | RuntimeException e) {
            git.close();
            throw e;
        }
    }
}
