package cz.wingit.core.log;

import java.time.Instant;
import java.util.List;

/**
 * Everything shown about one selected commit.
 *
 * @param id            full commit id
 * @param parents       full ids of the parents, first parent first
 * @param author        who made the change
 * @param authorTime    when the change was made
 * @param committer     who created the commit (differs from the author e.g. after a rebase or cherry-pick)
 * @param commitTime    when the commit was created
 * @param message       full commit message
 * @param files         files changed compared with the first parent, sorted by path
 */
public record CommitDetails(String id, List<String> parents, Person author, Instant authorTime,
                            Person committer, Instant commitTime, String message, List<CommitFile> files) {

    public CommitDetails {
        parents = List.copyOf(parents);
        files = List.copyOf(files);
    }

    /** Name and e-mail of an author or committer. */
    public record Person(String name, String email) {
    }
}
