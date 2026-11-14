package cz.wingit.ui.common;

import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;
import java.util.function.Supplier;

import javax.swing.SwingWorker;

/**
 * Runs a blocking read in the background and delivers only the latest result on the EDT.
 * <ul>
 *   <li>{@link #load} starts a new read; results of older reads are dropped.</li>
 *   <li>{@link #refresh} does not restart a running read (it could never finish under constant
 *       changes); instead the read is repeated once after it completes.</li>
 * </ul>
 * All methods must be called on the EDT.
 */
public final class LatestLoader<T> {

    private final Consumer<T> onResult;
    private final Consumer<Throwable> onError;
    private SwingWorker<T, Void> worker;
    private Supplier<T> task;
    private int generation;
    private boolean refreshQueued;

    public LatestLoader(Consumer<T> onResult, Consumer<Throwable> onError) {
        this.onResult = onResult;
        this.onError = onError;
    }

    public void load(Supplier<T> newTask) {
        cancel();
        task = newTask;
        int request = generation;
        worker = new SwingWorker<>() {
            @Override
            protected T doInBackground() {
                return newTask.get();
            }

            @Override
            protected void done() {
                if (request != generation || isCancelled()) {
                    return;
                }
                try {
                    onResult.accept(get());
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } catch (ExecutionException e) {
                    onError.accept(e.getCause() != null ? e.getCause() : e);
                }
                if (refreshQueued) {
                    refreshQueued = false;
                    load(newTask);
                }
            }
        };
        worker.execute();
    }

    /** Repeats the last task; if it is still running, repeats it once after it finishes. */
    public void refresh() {
        if (task == null) {
            return;
        }
        if (worker != null && !worker.isDone()) {
            refreshQueued = true;
            return;
        }
        load(task);
    }

    /** Drops any running read and forgets the task. */
    public void cancel() {
        generation++;
        refreshQueued = false;
        task = null;
        if (worker != null) {
            worker.cancel(false);
            worker = null;
        }
    }
}
