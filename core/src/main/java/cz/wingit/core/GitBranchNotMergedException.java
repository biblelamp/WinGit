package cz.wingit.core;

/**
 * A branch was not deleted because its commits are not merged into the current branch or its
 * upstream; deleting it with {@code force} could lose them.
 */
public final class GitBranchNotMergedException extends GitException {

    private static final long serialVersionUID = 1L;

    private final String branch;

    public GitBranchNotMergedException(String branch, Throwable cause) {
        super("Branch '" + branch + "' is not fully merged", cause);
        this.branch = branch;
    }

    public String branch() {
        return branch;
    }
}
