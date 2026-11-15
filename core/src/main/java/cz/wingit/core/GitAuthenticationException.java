package cz.wingit.core;

/**
 * A remote operation failed because the remote rejected or did not get credentials.
 * The caller may ask the user for credentials and retry.
 */
public final class GitAuthenticationException extends GitException {

    private static final long serialVersionUID = 1L;

    private final String url;
    private final String stderr;

    public GitAuthenticationException(String url, String stderr, Throwable cause) {
        super("Authentication failed for " + url, cause);
        this.url = url;
        this.stderr = stderr;
    }

    /** URL of the remote that needs credentials (as configured, may contain a user name). */
    public String url() {
        return url;
    }

    /** Raw error output of Git. */
    public String stderr() {
        return stderr;
    }
}
