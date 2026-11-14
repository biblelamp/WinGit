package cz.wingit.core.cli;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import cz.wingit.core.GitCancelledException;
import cz.wingit.core.GitCommandException;
import cz.wingit.core.GitException;

/**
 * Runs the native {@code git} executable.
 * <p>
 * Every call gets {@code core.quotepath=false} and {@code core.longpaths=true}; output is decoded
 * as UTF-8; stdin is closed and terminal prompts are disabled, so a missing credential fails
 * instead of hanging (Git Credential Manager still shows its own UI).
 * <p>
 * Output is buffered in memory, so this runner is meant for commands with small output
 * (init, clone, commit, ...). Large outputs such as {@code git log} need a streaming variant.
 */
public final class GitCli {

    private static final List<String> BASE_OPTIONS = List.of(
            "-c", "core.quotepath=false",
            "-c", "core.longpaths=true");

    private static final long KILL_WAIT_SECONDS = 5;

    private final String executable;

    /** Uses {@code git} from {@code PATH}. */
    public GitCli() {
        this("git");
    }

    public GitCli(String executable) {
        this.executable = executable;
    }

    public GitResult run(Path workDir, List<String> args) {
        return run(workDir, args, null);
    }

    /**
     * Runs {@code git <args>} in {@code workDir} and waits for it.
     *
     * @param progress receives stderr lines while the process runs (may be {@code null})
     * @throws GitCommandException  on a non-zero exit code
     * @throws GitCancelledException if the calling thread is interrupted; the process is killed
     * @throws GitException          if Git cannot be started
     */
    public GitResult run(Path workDir, List<String> args, Consumer<String> progress) {
        List<String> command = new ArrayList<>();
        command.add(executable);
        command.addAll(BASE_OPTIONS);
        command.addAll(args);

        ProcessBuilder builder = new ProcessBuilder(command).directory(workDir.toFile());
        builder.environment().put("GIT_TERMINAL_PROMPT", "0");

        Process process;
        try {
            process = builder.start();
        } catch (IOException e) {
            throw new GitException("Cannot start Git ('" + executable + "'). Is Git installed and on PATH?", e);
        }
        try {
            process.getOutputStream().close();
        } catch (IOException ignored) {
            // nothing to send anyway
        }

        StreamPump stdout = new StreamPump("git-stdout", process.getInputStream(), null);
        StreamPump stderr = new StreamPump("git-stderr", process.getErrorStream(), progress);
        stdout.start();
        stderr.start();

        int exitCode;
        try {
            exitCode = process.waitFor();
            stdout.join();
            stderr.join();
        } catch (InterruptedException e) {
            kill(process);
            Thread.currentThread().interrupt();
            throw new GitCancelledException();
        }

        if (exitCode != 0) {
            throw new GitCommandException(display(args), exitCode, stderr.text());
        }
        return new GitResult(exitCode, stdout.text(), stderr.text());
    }

    private static String display(List<String> args) {
        return "git " + String.join(" ", args);
    }

    /** Kills git and its helpers (e.g. git-remote-https) and waits briefly so files are released. */
    private static void kill(Process process) {
        List<ProcessHandle> handles = process.descendants().collect(Collectors.toList());
        handles.add(process.toHandle());
        handles.forEach(ProcessHandle::destroyForcibly);

        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(KILL_WAIT_SECONDS);
        for (ProcessHandle handle : handles) {
            long remaining = deadline - System.nanoTime();
            if (remaining <= 0) {
                break;
            }
            try {
                handle.onExit().get(remaining, TimeUnit.NANOSECONDS);
            } catch (Exception e) {
                // best effort
            }
        }
    }
}
