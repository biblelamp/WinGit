package cz.wingit.core.log;

import java.time.Instant;
import java.util.List;

/**
 * One commit as shown in the history list.
 *
 * @param id         full commit id
 * @param parents    full ids of the parent commits (first parent first; empty for a root commit)
 * @param message    first line of the commit message
 * @param author     author name
 * @param authorTime when the change was authored
 * @param refs       branches/tags pointing at this commit, in display order
 * @param unpushed   the commit is on no remote-tracking branch yet (only local); always {@code false}
 *                   in a repository without remote-tracking branches, where "pushed" has no meaning
 * @param unpulled   the commit is on the upstream of the checked-out branch (as last fetched) but not
 *                   on the branch itself yet: a pull would bring it
 */
public record CommitInfo(String id, List<String> parents, String message, String author, Instant authorTime,
                         List<RefLabel> refs, boolean unpushed, boolean unpulled) {

    public CommitInfo {
        parents = List.copyOf(parents);
        refs = List.copyOf(refs);
    }

    /** A commit that is not waiting to be pulled. */
    public CommitInfo(String id, List<String> parents, String message, String author, Instant authorTime,
                      List<RefLabel> refs, boolean unpushed) {
        this(id, parents, message, author, authorTime, refs, unpushed, false);
    }

    /** Abbreviated id (7 characters). */
    public String shortId() {
        return id.substring(0, Math.min(7, id.length()));
    }

    public boolean isMerge() {
        return parents.size() > 1;
    }
}
