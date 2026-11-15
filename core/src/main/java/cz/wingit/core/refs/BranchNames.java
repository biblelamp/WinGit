package cz.wingit.core.refs;

import java.util.Collection;
import java.util.Locale;

import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.Repository;

/**
 * Checks for new local branch names (pure, no repository access).
 */
public final class BranchNames {

    private BranchNames() {
    }

    /**
     * {@code true} if Git accepts {@code name} as a branch name ({@code git check-ref-format --branch}
     * rules: no spaces, {@code ..}, {@code ~^:?*[\}, no leading {@code -} or trailing {@code .lock}, ...).
     */
    public static boolean isValid(String name) {
        return name != null && !name.isEmpty() && !name.startsWith("-") && !Constants.HEAD.equals(name)
                && Repository.isValidRefName(Constants.R_HEADS + name);
    }

    /**
     * {@code true} if {@code name} clashes with an existing branch. Compared case-insensitively:
     * on Windows {@code Feature} and {@code feature} would share one ref file.
     */
    public static boolean exists(String name, Collection<String> existing) {
        String lower = name.toLowerCase(Locale.ROOT);
        return existing.stream().anyMatch(b -> b.toLowerCase(Locale.ROOT).equals(lower));
    }
}
