package cz.wingit.core.cli;

/**
 * Outcome of a finished {@code git} process.
 */
public record GitResult(int exitCode, String stdout, String stderr) {
}
