package cz.wingit.core;

/**
 * A {@code git} process finished with a non-zero exit code.
 */
public final class GitCommandException extends GitException {

    private static final long serialVersionUID = 1L;

    private final String command;
    private final int exitCode;
    private final String stderr;

    public GitCommandException(String command, int exitCode, String stderr) {
        super("'" + command + "' failed with exit code " + exitCode
                + (stderr.isBlank() ? "" : ": " + stderr.strip()));
        this.command = command;
        this.exitCode = exitCode;
        this.stderr = stderr;
    }

    public String command() {
        return command;
    }

    public int exitCode() {
        return exitCode;
    }

    /** Raw error output of the process (may be empty). */
    public String stderr() {
        return stderr;
    }
}
