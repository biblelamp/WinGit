package cz.wingit.ui.branches;

import javax.swing.tree.DefaultMutableTreeNode;

import cz.wingit.core.refs.LocalBranch;
import cz.wingit.core.refs.RemoteBranches;
import cz.wingit.core.refs.RepositoryRefs;
import cz.wingit.core.refs.Stash;
import cz.wingit.ui.Messages;

/**
 * Builds the tree: Local (with a detached HEAD entry if any), Remotes / remote, Tags, Stashes.
 * Groups show their item count.
 */
final class BranchTreeBuilder {

    static final String LOCAL = "local";
    static final String REMOTES = "remotes";
    static final String TAGS = "tags";
    static final String STASHES = "stashes";

    private BranchTreeBuilder() {
    }

    static DefaultMutableTreeNode build(RepositoryRefs refs) {
        DefaultMutableTreeNode root = new DefaultMutableTreeNode();

        int localCount = refs.localBranches().size() + (refs.detachedAt() != null ? 1 : 0);
        DefaultMutableTreeNode local = group(LOCAL, Messages.get("branches.local"), localCount);
        if (refs.detachedAt() != null) {
            local.add(leaf("detached", Messages.format("branches.detached", refs.detachedAt()), null, true, null));
        }
        for (LocalBranch branch : refs.localBranches()) {
            local.add(leaf("branch:" + branch.name(), branch.name(), trackingDetail(branch), branch.current(),
                    branch.current() ? null : BranchItem.Target.local(branch.name())));
        }
        root.add(local);

        DefaultMutableTreeNode remotes = group(REMOTES, Messages.get("branches.remote"), refs.remotes().size());
        for (RemoteBranches remote : refs.remotes()) {
            DefaultMutableTreeNode node = group("remote:" + remote.remote(), remote.remote(), remote.branches().size());
            for (String branch : remote.branches()) {
                node.add(leaf("remote-branch:" + remote.remote() + "/" + branch, branch, null, false,
                        new BranchItem.Target(remote.remote(), branch)));
            }
            remotes.add(node);
        }
        root.add(remotes);

        DefaultMutableTreeNode tags = group(TAGS, Messages.get("branches.tags"), refs.tags().size());
        for (String tag : refs.tags()) {
            tags.add(leaf("tag:" + tag, tag, null, false, null));
        }
        root.add(tags);

        DefaultMutableTreeNode stashes = group(STASHES, Messages.get("branches.stashes"), refs.stashes().size());
        for (Stash stash : refs.stashes()) {
            stashes.add(leaf("stash:" + stash.index(), "stash@{" + stash.index() + "}", stash.message(), false, null));
        }
        root.add(stashes);
        return root;
    }

    /** E.g. {@code origin/main \u21912 \u21931}; {@code null} without upstream. */
    static String trackingDetail(LocalBranch branch) {
        if (branch.upstream() == null) {
            return null;
        }
        StringBuilder detail = new StringBuilder(branch.upstream());
        if (branch.ahead() > 0) {
            detail.append(" \u2191").append(branch.ahead());
        }
        if (branch.behind() > 0) {
            detail.append(" \u2193").append(branch.behind());
        }
        return detail.toString();
    }

    private static DefaultMutableTreeNode group(String key, String name, int count) {
        String label = Messages.format("branches.group", name, String.valueOf(count));
        return new DefaultMutableTreeNode(new BranchItem(key, label, null, false, null));
    }

    private static DefaultMutableTreeNode leaf(String key, String label, String detail, boolean current,
                                               BranchItem.Target target) {
        return new DefaultMutableTreeNode(new BranchItem(key, label, detail, current, target), false);
    }
}
