package cz.wingit.ui.commit;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import cz.wingit.core.AmendCheck;
import cz.wingit.ui.Messages;

class CommitAmendTest {

    @Test
    void amendTooltipSaysWhyAmendIsNotPossible() {
        assertThat(CommitDialog.amendTooltipKey(AmendCheck.State.POSSIBLE, false)).isEqualTo("commit.amend.tooltip");
        assertThat(CommitDialog.amendTooltipKey(AmendCheck.State.POSSIBLE, true)).isEqualTo("commit.amend.only");
        assertThat(CommitDialog.amendTooltipKey(AmendCheck.State.PUSHED, false)).isEqualTo("commit.amend.pushed");
        assertThat(CommitDialog.amendTooltipKey(AmendCheck.State.NO_COMMIT, false)).isEqualTo("commit.amend.noCommit");
        assertThat(CommitDialog.amendTooltipKey(AmendCheck.State.OPERATION_IN_PROGRESS, false))
                .isEqualTo("commit.amend.inProgress");
        for (AmendCheck.State state : AmendCheck.State.values()) {
            assertThat(Messages.get(CommitDialog.amendTooltipKey(state, false))).isNotBlank();
        }
    }
}
