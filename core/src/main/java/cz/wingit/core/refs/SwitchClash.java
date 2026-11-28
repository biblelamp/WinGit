package cz.wingit.core.refs;

/**
 * A local change that would not fit the branch being switched to: bringing it back there after the
 * switch would end in a conflict.
 *
 * @param path   repository-relative path
 * @param reason why it does not fit
 */
public record SwitchClash(String path, Reason reason) {

    /** Why a change does not fit the other branch. */
    public enum Reason {
        /** The file is changed locally but does not exist on the other branch. */
        MISSING_THERE,
        /** The file was changed on the other branch too, in a way that clashes. */
        CHANGED_THERE,
        /** A new (untracked) file whose name the other branch already uses. */
        EXISTS_THERE
    }
}
