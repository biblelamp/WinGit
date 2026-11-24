package cz.wingit.ui.remote;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.locks.Lock;
import java.util.function.Consumer;
import java.util.function.Supplier;

import javax.swing.SwingUtilities;
import javax.swing.Timer;

import cz.wingit.core.GitService;

/**
 * Keeps "new on the server" up to date without the user clicking Fetch: a repository is fetched when
 * it is opened, and all repositories of the list periodically ({@link #setInterval}, by default every
 * {@link #INTERVAL}; the periodic fetch can be switched off). Runs in the background, one
 * repository at a time, and never asks for credentials ({@link GitService#fetchInBackground}); a failed
 * fetch (offline, not signed in, ...) is silently skipped, the markers then show the last fetched state.
 */
public final class AutoFetch {

    /** How often all repositories of the list are fetched unless {@link #setInterval} says otherwise. */
    public static final Duration INTERVAL = Duration.ofMinutes(10);

    /** A background fetch running longer is cancelled (e.g. a stuck connection) and tried again next time. */
    static final Duration TIMEOUT = Duration.ofMinutes(2);

    private final GitService git;
    private final Consumer<Path> onChanged;
    /** Repositories waiting for their fetch, so a repository is not queued twice. */
    private final Set<Path> queued = ConcurrentHashMap.newKeySet();
    private final ExecutorService queue = Executors.newSingleThreadExecutor(daemon("wingit-auto-fetch"));
    private final ExecutorService runner = Executors.newSingleThreadExecutor(daemon("wingit-auto-fetch-git"));
    private final Timer timer;
    /** Whether the periodic fetch runs ({@link #setInterval} with an empty interval switches it off). */
    private boolean periodic = true;
    private boolean started;

    /**
     * @param repositories the repositories to fetch periodically; called on the EDT
     * @param onChanged    called on the EDT with a repository whose fetch brought something new
     */
    public AutoFetch(GitService git, Supplier<List<Path>> repositories, Consumer<Path> onChanged) {
        this.git = git;
        this.onChanged = onChanged;
        int millis = (int) INTERVAL.toMillis();
        timer = new Timer(millis, e -> repositories.get().forEach(this::fetch));
        timer.setInitialDelay(millis);
    }

    /** Starts the periodic fetch (unless it is switched off). Call on the EDT. */
    public void start() {
        started = true;
        if (periodic) {
            timer.start();
        }
    }

    /**
     * Changes how often all repositories are fetched; empty switches the periodic fetch off (fetching a
     * repository when it is opened stays). The next periodic fetch comes one full interval from now.
     * Call on the EDT.
     */
    public void setInterval(Optional<Duration> interval) {
        periodic = interval.isPresent();
        timer.stop();
        interval.ifPresent(d -> {
            int millis = (int) Math.min(Integer.MAX_VALUE, d.toMillis());
            timer.setDelay(millis);
            timer.setInitialDelay(millis);
        });
        if (periodic && started) {
            timer.start();
        }
    }

    /** Current period, empty when the periodic fetch is off (tests). */
    Optional<Duration> interval() {
        return periodic ? Optional.of(Duration.ofMillis(timer.getDelay())) : Optional.empty();
    }

    /** Whether the periodic timer runs (tests). */
    boolean isRunning() {
        return timer.isRunning();
    }

    /** Fetches {@code workTree} in the background soon, unless it is waiting already. Any thread. */
    public void fetch(Path workTree) {
        if (queued.add(workTree)) {
            queue.execute(() -> run(workTree));
        }
    }

    private void run(Path workTree) {
        Lock network = RemoteOperation.networkLock();
        try {
            if (!Files.isDirectory(workTree)) {
                return;
            }
            network.lockInterruptibly(); // waits while the user's own push, pull, ... runs
            boolean changed;
            try {
                Future<Boolean> fetch = runner.submit(() -> git.fetchInBackground(workTree));
                try {
                    changed = fetch.get(TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
                } catch (TimeoutException e) {
                    fetch.cancel(true); // interrupting kills Git
                    return;
                }
            } finally {
                network.unlock();
            }
            if (changed) {
                SwingUtilities.invokeLater(() -> onChanged.accept(workTree));
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (ExecutionException e) {
            // offline, not signed in, no access, ...: try again next time
        } finally {
            queued.remove(workTree);
        }
    }

    private static java.util.concurrent.ThreadFactory daemon(String name) {
        return r -> {
            Thread thread = new Thread(r, name);
            thread.setDaemon(true);
            return thread;
        };
    }
}
