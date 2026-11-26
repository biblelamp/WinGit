package cz.wingit.ui.branches;

/**
 * Content of one node of the branches tree.
 *
 * @param key     stable identity across refreshes (used to keep groups expanded), e.g. {@code local},
 *                {@code remote:origin}, {@code branch:main}
 * @param label   main text
 * @param detail  dimmed text after the label (upstream, ahead/behind, stash message), or {@code null}
 * @param current whether this is the checked-out branch (shown bold)
 * @param target  branch checked out on double-click / Enter, or {@code null} if the node has none
 * @param tooltip tooltip text, or {@code null}
 */
record BranchItem(String key, String label, String detail, boolean current, Target target, String tooltip) {

    BranchItem(String key, String label, String detail, boolean current, Target target) {
        this(key, label, detail, current, target, null);
    }

    /**
     * A branch that can be checked out.
     *
     * @param remote remote name for a remote-tracking branch, {@code null} for a local branch
     * @param branch branch name without the remote prefix
     */
    record Target(String remote, String branch) {

        static Target local(String branch) {
            return new Target(null, branch);
        }

        boolean isRemote() {
            return remote != null;
        }
    }

    @Override
    public String toString() {
        return detail == null ? label : label + " " + detail;
    }
}
