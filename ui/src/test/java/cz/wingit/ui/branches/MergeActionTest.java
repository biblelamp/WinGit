package cz.wingit.ui.branches;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import cz.wingit.core.refs.LocalBranch;
import cz.wingit.core.refs.RemoteBranches;
import cz.wingit.core.refs.RepositoryRefs;

class MergeActionTest {

    @Test
    void candidatesAreOtherLocalBranchesThenRemoteBranches() {
        RepositoryRefs refs = new RepositoryRefs(
                List.of(new LocalBranch("feature", null, 0, 0, false),
                        new LocalBranch("main", "origin/main", 0, 0, true)),
                null,
                List.of(new RemoteBranches("origin", List.of("feature", "main"))),
                List.of(), List.of());

        assertThat(MergeAction.candidates(refs)).containsExactly("feature", "origin/feature", "origin/main");
    }
}
