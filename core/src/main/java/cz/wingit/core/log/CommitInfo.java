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
 */
public record CommitInfo(String id, List<String> parents, String message, String author, Instant authorTime,
                         List<RefLabel> refs) {

    public CommitInfo {
        parents = List.copyOf(parents);
        refs = List.copyOf(refs);
    }

    /** Abbreviated id (7 characters). */
    public String shortId() {
        return id.substring(0, Math.min(7, id.length()));
    }

    public boolean isMerge() {
        return parents.size() > 1;
    }
}
