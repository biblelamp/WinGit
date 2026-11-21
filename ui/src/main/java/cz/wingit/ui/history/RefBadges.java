package cz.wingit.ui.history;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import cz.wingit.core.log.RefLabel;

/**
 * What the History shows for the refs of one commit. A local branch and the remote branch of the same
 * name on the same commit (e.g. {@code main} and {@code origin/main}) become one badge "main | origin",
 * so a long branch name is not shown twice. Long names are shortened; the tooltip has them in full.
 */
final class RefBadges {

    /** Longer names are cut to this many characters, the last one being an ellipsis. */
    static final int MAX_NAME_LENGTH = 40;

    /**
     * One badge.
     *
     * @param label  the ref (for a merged badge, the local branch)
     * @param text   the name as shown, possibly shortened
     * @param remote the remote whose branch of the same name is on this commit too, or {@code null}
     */
    record Badge(RefLabel label, String text, String remote) {

        boolean shortened() {
            return !text.equals(label.name());
        }
    }

    private RefBadges() {
    }

    static List<Badge> of(List<RefLabel> refs) {
        List<RefLabel> merged = new ArrayList<>();
        Map<RefLabel, String> remotes = new HashMap<>();
        for (RefLabel ref : refs) {
            if (ref.kind() == RefLabel.Kind.LOCAL_BRANCH) {
                RefLabel remote = sameNameOnRemote(ref, refs, merged);
                if (remote != null) {
                    merged.add(remote);
                    remotes.put(ref, remoteOf(remote, ref));
                }
            }
        }
        List<Badge> badges = new ArrayList<>();
        for (RefLabel ref : refs) {
            if (!merged.contains(ref)) {
                badges.add(new Badge(ref, shorten(ref.name()), remotes.get(ref)));
            }
        }
        return badges;
    }

    /** Full names of all refs, for the tooltip when a badge is shortened; otherwise {@code null}. */
    static String fullNames(List<Badge> badges) {
        if (badges.stream().noneMatch(Badge::shortened)) {
            return null;
        }
        return badges.stream()
                .map(b -> b.remote() == null ? b.label().name() : b.label().name() + ", " + b.remote() + "/" + b.label().name())
                .collect(Collectors.joining(", "));
    }

    static String shorten(String name) {
        return name.length() <= MAX_NAME_LENGTH ? name : name.substring(0, MAX_NAME_LENGTH - 1) + "…";
    }

    /** The remote branch {@code <remote>/<local name>} on this commit; prefers {@code origin}. */
    private static RefLabel sameNameOnRemote(RefLabel local, List<RefLabel> refs, List<RefLabel> taken) {
        RefLabel found = null;
        for (RefLabel ref : refs) {
            if (ref.kind() == RefLabel.Kind.REMOTE_BRANCH && !taken.contains(ref) && remoteOf(ref, local) != null) {
                if (found == null || "origin".equals(remoteOf(ref, local))) {
                    found = ref;
                }
            }
        }
        return found;
    }

    /** {@code origin} for {@code origin/feature/x} and local {@code feature/x}; {@code null} if the names differ. */
    private static String remoteOf(RefLabel remoteBranch, RefLabel local) {
        String name = remoteBranch.name();
        String suffix = "/" + local.name();
        if (!name.endsWith(suffix) || name.length() == suffix.length()) {
            return null;
        }
        String remote = name.substring(0, name.length() - suffix.length());
        return remote.contains("/") ? null : remote;
    }
}
