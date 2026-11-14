package cz.wingit.core;

/**
 * Base class for failures of Git operations.
 */
public class GitException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public GitException(String message) {
        super(message);
    }

    public GitException(String message, Throwable cause) {
        super(message, cause);
    }
}
