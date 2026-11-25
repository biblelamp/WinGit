package cz.wingit.core.log;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.eclipse.jgit.lib.BranchConfig;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;

/**
 * A cheap fingerprint of everything a shown history depends on besides the commits themselves: where
 * HEAD points, every branch, remote-tracking branch and tag, and the upstream of the current branch.
 * Commits never change, so as long as the fingerprint stays the same, a history read earlier is still
 * correct (including its labels and unpushed/unpulled marks) and need not be read again. Stashes do not
 * count: they are not part of the history.
 */
public final class HistoryVersion {

    private HistoryVersion() {
    }

    /** The fingerprint; compare with {@link String#equals}. Reads only refs and config, no commits. */
    public static String of(Repository repository) throws IOException {
        StringBuilder version = new StringBuilder();
        Ref head = repository.exactRef(Constants.HEAD);
        if (head != null) {
            version.append("HEAD ").append(head.getTarget().getName()).append(' ').append(id(head)).append('\n');
            String branch = repository.getBranch();
            if (branch != null && !ObjectId.isId(branch)) {
                version.append("upstream ")
                        .append(new BranchConfig(repository.getConfig(), branch).getRemoteTrackingBranch())
                        .append('\n');
            }
        }
        List<Ref> refs = new ArrayList<>(repository.getRefDatabase().getRefsByPrefix(Constants.R_REFS));
        refs.sort(Comparator.comparing(Ref::getName));
        for (Ref ref : refs) {
            if (ref.getName().equals(Constants.R_STASH)) {
                continue;
            }
            version.append(ref.getName()).append(' ').append(id(ref)).append('\n');
        }
        return version.toString();
    }

    private static String id(Ref ref) {
        ObjectId id = ref.getObjectId();
        return id == null ? "-" : id.name();
    }
}
