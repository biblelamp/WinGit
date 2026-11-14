package cz.wingit.core;

/**
 * A Git operation was cancelled (its thread was interrupted and the process killed).
 */
public final class GitCancelledException extends GitException {

    private static final long serialVersionUID = 1L;

    public GitCancelledException() {
        super("Git operation was cancelled");
    }
}
