package cz.wingit.core.cli;

/**
 * A Git command that was run, for showing the user what happened (like a console transcript).
 *
 * @param command  command line without WinGit's internal options, e.g. {@code git switch main}
 * @param exitCode exit code of the process; 0 means success
 * @param output   what Git printed (standard output, then error output), stripped
 */
public record LoggedCommand(String command, int exitCode, String output) {

    public boolean succeeded() {
        return exitCode == 0;
    }
}
