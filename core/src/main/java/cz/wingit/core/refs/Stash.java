package cz.wingit.core.refs;

/**
 * A stash entry: local changes put aside with {@code git stash}.
 *
 * @param index   position, as in {@code stash@{index}} (0 = newest)
 * @param id      full id of the stash commit; operations check it, so they never hit another stash
 *                when the list changed in the meantime
 * @param message first line of the stash message
 */
public record Stash(int index, String id, String message) {

    /** The name Git uses for it, e.g. {@code stash@{0}}. */
    public String ref() {
        return "stash@{" + index + "}";
    }
}
