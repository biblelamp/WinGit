package cz.wingit.core.refs;

import java.util.Collection;
import java.util.Comparator;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.Repository;

/**
 * Checks and suggestions for new tag names (pure, no repository access).
 */
public final class TagNames {

    /** {@code 1.2}, {@code v1.2.3}, {@code V10.0.1}; no pre-release or build suffix. */
    private static final Pattern VERSION = Pattern.compile("([vV]?)(\\d{1,9})\\.(\\d{1,9})(?:\\.(\\d{1,9}))?");

    private TagNames() {
    }

    /** {@code true} if Git accepts {@code name} as a tag name (same rules as for branches). */
    public static boolean isValid(String name) {
        return name != null && !name.isEmpty() && !name.startsWith("-") && !Constants.HEAD.equals(name)
                && Repository.isValidRefName(Constants.R_TAGS + name);
    }

    /**
     * {@code true} if {@code name} clashes with an existing tag; compared case-insensitively like
     * branches ({@link BranchNames#exists}).
     */
    public static boolean exists(String name, Collection<String> existing) {
        return BranchNames.exists(name, existing);
    }

    /**
     * The version after the highest version-like tag, in the same form: {@code v0.2.1} gives
     * {@code v0.2.2}, {@code 1.4} gives {@code 1.5}. Empty if no tag looks like a version.
     */
    public static Optional<String> nextVersion(Collection<String> tags) {
        return tags.stream().map(VERSION::matcher).filter(Matcher::matches).map(Version::of)
                .max(Comparator.comparingLong(Version::major).thenComparingLong(Version::minor)
                        .thenComparingLong(Version::patch))
                .map(Version::next);
    }

    private record Version(String prefix, long major, long minor, long patch) {

        static Version of(Matcher m) {
            return new Version(m.group(1), Long.parseLong(m.group(2)), Long.parseLong(m.group(3)),
                    m.group(4) != null ? Long.parseLong(m.group(4)) : -1);
        }

        String next() {
            return patch >= 0 ? prefix + major + "." + minor + "." + (patch + 1) : prefix + major + "." + (minor + 1);
        }
    }
}
