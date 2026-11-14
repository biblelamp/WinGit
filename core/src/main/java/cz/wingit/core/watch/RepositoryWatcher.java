package cz.wingit.core.watch;

import static java.nio.file.StandardWatchEventKinds.ENTRY_CREATE;
import static java.nio.file.StandardWatchEventKinds.ENTRY_DELETE;
import static java.nio.file.StandardWatchEventKinds.ENTRY_MODIFY;
import static java.nio.file.StandardWatchEventKinds.OVERFLOW;

import java.io.IOException;
import java.nio.file.ClosedWatchServiceException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;

import com.sun.nio.file.ExtendedWatchEventModifier;

/**
 * Watches a working tree and reports "something relevant changed", debounced.
 * <p>
 * Uses one recursive watch ({@code FILE_TREE}) where supported (Windows), otherwise registers every
 * directory. Ignored: {@code .git} internals that do not affect status (objects, logs, lock files),
 * and paths matched by the {@code ignored} predicate (e.g. .gitignore rules). Events are collected
 * and reported once after {@code debounce} of quiet, or at the latest after {@link #MAX_WAIT}.
 * The callback runs on the watcher's scheduler thread.
 */
public final class RepositoryWatcher implements RepositoryWatch {

    public static final Duration DEFAULT_DEBOUNCE = Duration.ofMillis(300);
    static final Duration MAX_WAIT = Duration.ofSeconds(2);

    /** More changed paths than this in one batch are not checked one by one; we just refresh. */
    private static final int MAX_CHECKED_PATHS = 256;

    @SuppressWarnings("rawtypes")
    private static final WatchEvent.Kind[] KINDS = {ENTRY_CREATE, ENTRY_DELETE, ENTRY_MODIFY};

    private final Path root;
    private final Predicate<String> ignored;
    private final Runnable onChange;
    private final long debounceNanos;
    private final WatchService watchService;
    private final ScheduledExecutorService scheduler;
    private final Map<WatchKey, Path> keys = new ConcurrentHashMap<>();
    private final boolean registerEachDirectory;
    private final Thread pollThread;

    // guarded by this
    private final Set<String> pending = new LinkedHashSet<>();
    private boolean overflow;
    private long firstPendingAt;
    private ScheduledFuture<?> scheduledFlush;

    private volatile boolean closed;

    private RepositoryWatcher(Path root, Predicate<String> ignored, Duration debounce, Runnable onChange)
            throws IOException {
        this.root = root.toAbsolutePath().normalize();
        this.ignored = ignored;
        this.onChange = onChange;
        this.debounceNanos = debounce.toNanos();
        this.watchService = this.root.getFileSystem().newWatchService();
        String name = "wingit-watch-" + this.root.getFileName();
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> daemon(r, name + "-flush"));

        boolean eachDirectory;
        try {
            eachDirectory = registerRoot();
        } catch (IOException | RuntimeException e) {
            closeQuietly();
            throw e;
        }
        this.registerEachDirectory = eachDirectory;
        this.pollThread = daemon(this::pollLoop, name);
        this.pollThread.start();
    }

    /**
     * Starts watching {@code workTree}.
     *
     * @param ignored  tells whether a repository-relative path ({@code /}-separated) is ignored;
     *                 called on the watcher thread
     * @param onChange called on the watcher thread after relevant changes
     */
    public static RepositoryWatcher start(Path workTree, Predicate<String> ignored, Duration debounce,
                                          Runnable onChange) throws IOException {
        return new RepositoryWatcher(workTree, ignored, debounce, onChange);
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        closeQuietly();
        pollThread.interrupt();
        try {
            scheduler.awaitTermination(1, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Whether a change of {@code relativePath} can affect the status. Inside {@code .git} only the
     * index, HEAD-like files, refs and info/exclude matter.
     */
    static boolean isRelevant(String relativePath) {
        if (relativePath.isEmpty() || relativePath.equals(".git")) {
            return false;
        }
        if (!relativePath.startsWith(".git/")) {
            return true;
        }
        String inside = relativePath.substring(".git/".length());
        if (inside.endsWith(".lock")) {
            return false;
        }
        return inside.equals("index")
                || inside.equals("HEAD")
                || inside.equals("MERGE_HEAD")
                || inside.equals("CHERRY_PICK_HEAD")
                || inside.equals("REVERT_HEAD")
                || inside.equals("packed-refs")
                || inside.equals("info/exclude")
                || inside.startsWith("refs/");
    }

    private void pollLoop() {
        while (!closed) {
            WatchKey key;
            try {
                key = watchService.take();
            } catch (InterruptedException | ClosedWatchServiceException e) {
                return;
            }
            Path dir = keys.get(key);
            for (WatchEvent<?> event : key.pollEvents()) {
                if (event.kind() == OVERFLOW || dir == null) {
                    record(null);
                    continue;
                }
                Path child = dir.resolve((Path) event.context());
                if (registerEachDirectory && event.kind() == ENTRY_CREATE
                        && Files.isDirectory(child, LinkOption.NOFOLLOW_LINKS)) {
                    try {
                        registerTree(child);
                    } catch (IOException e) {
                        // the directory vanished again; nothing to watch
                    }
                }
                record(root.relativize(child).toString().replace('\\', '/'));
            }
            if (!key.reset()) {
                keys.remove(key);
                if (keys.isEmpty()) {
                    return; // the working tree itself is gone
                }
            }
        }
    }

    /** Collects a changed path ({@code null} = unknown changes) and (re)schedules the flush. */
    private synchronized void record(String relativePath) {
        if (closed) {
            return;
        }
        if (relativePath == null || pending.size() >= MAX_CHECKED_PATHS) {
            overflow = true;
        } else if (isRelevant(relativePath)) {
            pending.add(relativePath);
        } else {
            return;
        }
        long now = System.nanoTime();
        if (scheduledFlush == null) {
            firstPendingAt = now;
        } else {
            scheduledFlush.cancel(false);
        }
        long untilMaxWait = firstPendingAt + MAX_WAIT.toNanos() - now;
        long delay = Math.max(0, Math.min(debounceNanos, untilMaxWait));
        try {
            scheduledFlush = scheduler.schedule(this::flush, delay, TimeUnit.NANOSECONDS);
        } catch (java.util.concurrent.RejectedExecutionException e) {
            // closing
        }
    }

    private void flush() {
        List<String> paths;
        boolean unknown;
        synchronized (this) {
            paths = new ArrayList<>(pending);
            unknown = overflow;
            pending.clear();
            overflow = false;
            scheduledFlush = null;
        }
        if (closed || !(unknown || paths.stream().anyMatch(this::notIgnored))) {
            return;
        }
        try {
            onChange.run();
        } catch (RuntimeException e) {
            // a failing listener must not stop the watcher
        }
    }

    private boolean notIgnored(String relativePath) {
        if (relativePath.startsWith(".git/")) {
            return true;
        }
        try {
            return !ignored.test(relativePath);
        } catch (RuntimeException e) {
            return true;
        }
    }

    /** @return {@code true} if every directory has to be registered (no recursive watch support) */
    private boolean registerRoot() throws IOException {
        try {
            keys.put(register(root, true), root);
            return false;
        } catch (UnsupportedOperationException e) {
            registerTree(root);
            return true;
        }
    }

    private WatchKey register(Path dir, boolean wholeTree) throws IOException {
        if (wholeTree) {
            return dir.register(watchService, KINDS, ExtendedWatchEventModifier.FILE_TREE);
        }
        return dir.register(watchService, KINDS);
    }

    private void registerTree(Path start) throws IOException {
        Files.walkFileTree(start, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                String relative = root.relativize(dir).toString().replace('\\', '/');
                if (relative.equals(".git/objects") || relative.equals(".git/logs")) {
                    return FileVisitResult.SKIP_SUBTREE;
                }
                keys.put(register(dir, false), dir);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFileFailed(Path file, IOException exc) {
                return FileVisitResult.CONTINUE;
            }
        });
    }

    private void closeQuietly() {
        scheduler.shutdownNow();
        try {
            watchService.close();
        } catch (IOException e) {
            // nothing more to do
        }
    }

    private static Thread daemon(Runnable runnable, String name) {
        Thread thread = new Thread(runnable, name);
        thread.setDaemon(true);
        return thread;
    }
}
