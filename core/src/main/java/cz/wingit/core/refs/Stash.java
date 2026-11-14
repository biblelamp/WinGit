package cz.wingit.core.refs;

/**
 * A stash entry.
 *
 * @param index   position, as in {@code stash@{index}} (0 = newest)
 * @param message first line of the stash message
 */
public record Stash(int index, String message) {
}
