package cz.wingit.core.refs;

import java.util.List;
import java.util.regex.Pattern;

/**
 * Branches of one remote, as last fetched ({@code refs/remotes/<remote>/...}), without {@code HEAD}.
 *
 * @param branches branch names without the remote prefix, sorted
 * @param url      fetch URL ({@code remote.<name>.url}) without a password, or {@code null} if not configured
 */
public record RemoteBranches(String remote, List<String> branches, String url) {

    /** {@code scheme://user:password@host} - the password part. */
    private static final Pattern PASSWORD = Pattern.compile("(://[^/@:]*):[^/@]*@");

    public RemoteBranches {
        branches = List.copyOf(branches);
        url = url == null || url.isBlank() ? null : withoutPassword(url.strip());
    }

    /** Without a known URL. */
    public RemoteBranches(String remote, List<String> branches) {
        this(remote, branches, null);
    }

    /** Never show a password someone put into the URL; the user name stays. */
    static String withoutPassword(String url) {
        return PASSWORD.matcher(url).replaceFirst("$1@");
    }
}
