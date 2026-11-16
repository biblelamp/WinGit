package cz.wingit.ui.tags;

import java.util.Collection;

import cz.wingit.core.refs.TagNames;
import cz.wingit.ui.Messages;

/**
 * Checks a new tag name typed by the user, shared by the New Tag and Commit dialogs.
 */
public final class TagNameCheck {

    private TagNameCheck() {
    }

    /** Why {@code name} cannot be used, or {@code null} if it can (or nothing is typed yet). */
    public static String problem(String name, Collection<String> existing) {
        if (name.isEmpty()) {
            return null;
        }
        if (!TagNames.isValid(name)) {
            return Messages.get("newTag.error.invalid");
        }
        if (TagNames.exists(name, existing)) {
            return Messages.format("newTag.error.exists", name);
        }
        return null;
    }
}
