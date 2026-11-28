package cz.wingit.core.cli;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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
 * as UTF-8; stdin is closed (or receives the given input) and terminal prompts are disabled, so a missing credential fails
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
        return run(workDir, args, progress, Map.of(), null);
    }

    /**
     * Runs {@code git <args>} in {@code workDir} with extra environment variables and optional input.
     *
     * @param progress    receives stderr lines while the process runs (may be {@code null})
     * @param environment variables added to the inherited environment
     * @param stdin       text written (UTF-8) to the process input before it is closed (may be {@code null})
     * @throws GitCommandException  on a non-zero exit code
     * @throws GitCancelledException if the calling thread is interrupted; the process is killed
     * @throws GitException          if Git cannot be started
     */
    public GitResult run(Path workDir, List<String> args, Consumer<String> progress,
                         Map<String, String> environment, String stdin) {
        GitResult result = execute(workDir, args, progress, environment, stdin);
        if (result.exitCode() != 0) {
            throw new GitCommandException(display(args), result.exitCode(), result.stderr());
        }
        return result;
    }

    /**
     * Runs {@code git <args>} like {@link #run(Path, List)}, but also returns (with its output) when Git
     * exits with one of {@code allowedExitCodes}, for commands that report a result by the exit code
     * (e.g. {@code git merge-tree}: 1 means conflicts).
     *
     * @throws GitCommandException on any other non-zero exit code
     */
    public GitResult runAllowing(Path workDir, List<String> args, int... allowedExitCodes) {
        GitResult result = execute(workDir, args, null, Map.of(), null);
        if (result.exitCode() == 0) {
            return result;
        }
        for (int allowed : allowedExitCodes) {
            if (result.exitCode() == allowed) {
                return result;
            }
        }
        throw new GitCommandException(display(args), result.exitCode(), result.stderr());
    }

    /**
     * Runs {@code git <args>} and logs it to {@code log}, whatever the exit code; for operations whose
     * steps are shown to the user.
     *
     * @return the logged command (check {@link LoggedCommand#succeeded()})
     * @throws GitCancelledException if the calling thread is interrupted
     * @throws GitException          if Git cannot be started
     */
    public LoggedCommand runLogged(Path workDir, List<String> args, List<LoggedCommand> log) {
        GitResult result = execute(workDir, args, null, Map.of(), null);
        String output = (result.stdout().strip() + "\n" + result.stderr().strip()).strip();
        LoggedCommand command = new LoggedCommand(display(args), result.exitCode(), output);
        log.add(command);
        return command;
    }

    private GitResult execute(Path workDir, List<String> args, Consumer<String> progress,
                              Map<String, String> environment, String stdin) {
        List<String> command = new ArrayList<>();
        command.add(executable);
        command.addAll(BASE_OPTIONS);
        command.addAll(args);

        ProcessBuilder builder = new ProcessBuilder(command).directory(workDir.toFile());
        builder.environment().put("GIT_TERMINAL_PROMPT", "0");
        builder.environment().putAll(environment);

        Process process;
        try {
            process = builder.start();
        } catch (IOException e) {
            throw new GitException("Cannot start Git ('" + executable + "'). Is Git installed and on PATH?", e);
        }
        StreamPump stdout = new StreamPump("git-stdout", process.getInputStream(), null);
        StreamPump stderr = new StreamPump("git-stderr", process.getErrorStream(), progress);
        stdout.start();
        stderr.start();
        writeInput(process, stdin);

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

        return new GitResult(exitCode, stdout.text(), stderr.text());
    }

    /** Sends {@code stdin} (if any) and closes the input; pumps already drain the outputs. */
    private static void writeInput(Process process, String stdin) {
        try (OutputStream in = process.getOutputStream()) {
            if (stdin != null) {
                in.write(stdin.getBytes(StandardCharsets.UTF_8));
            }
        } catch (IOException ignored) {
            // Git exited without reading everything; its exit code and stderr tell why.
        }
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
