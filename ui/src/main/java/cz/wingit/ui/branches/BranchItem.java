package cz.wingit.ui.branches;

/**
 * Content of one node of the branches tree.
 *
 * @param key     stable identity across refreshes (used to keep groups expanded), e.g. {@code local},
 *                {@code remote:origin}, {@code branch:main}
 * @param label   main text
 * @param detail  dimmed text after the label (upstream, ahead/behind, stash message), or {@code null}
 * @param current whether this is the checked-out branch (shown bold)
 */
record BranchItem(String key, String label, String detail, boolean current) {

    @Override
    public String toString() {
        return detail == null ? label : label + " " + detail;
    }
}
