package cz.wingit.ui.main;

import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.swing.SwingUtilities;

import cz.wingit.core.GitException;
import cz.wingit.core.GitService;
import cz.wingit.core.watch.RepositoryWatch;

/**
 * Keeps one file watch on the opened repository and runs {@code onChange} on the EDT when it
 * changes. Watches are started and stopped on a background thread (registration touches the disk).
 */
final class AutoRefresh {

    private final GitService git;
    private final Runnable onChange;
    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "wingit-watch-control");
        thread.setDaemon(true);
        return thread;
    });

    /** Written on the EDT, read by watcher threads to drop late notifications. */
    private volatile Path watched;
    /** Only touched on {@link #executor}. */
    private RepositoryWatch watch;

    AutoRefresh(GitService git, Runnable onChange) {
        this.git = git;
        this.onChange = onChange;
    }

    /** Switches the watch to {@code workTree} (or stops watching). Call on the EDT. */
    void watch(Optional<Path> workTree) {
        Path path = workTree.orElse(null);
        watched = path;
        executor.execute(() -> {
            if (watch != null) {
                watch.close();
                watch = null;
            }
            if (path == null || !Objects.equals(path, watched)) {
                return; // nothing to watch, or already switched again
            }
            try {
                watch = git.watch(path, () -> SwingUtilities.invokeLater(() -> {
                    if (Objects.equals(path, watched)) {
                        onChange.run();
                    }
                }));
            } catch (GitException e) {
                // No auto-refresh for this repository (e.g. folder removed); the status pane shows errors.
            }
        });
    }
}
