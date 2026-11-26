package cz.wingit.ui.branches;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import cz.wingit.core.merge.MergeConflicts;
import cz.wingit.core.merge.MergePreview;
import cz.wingit.ui.Messages;

class MergeDialogTest {

    private static MergePreview preview(MergePreview.Kind kind, int incoming, int currentOnly, int localChanges) {
        return new MergePreview(kind, List.of(), incoming, currentOnly, 3, localChanges);
    }

    @Test
    void summaryExplainsFastForwardDependingOnMode() {
        MergePreview ff = preview(MergePreview.Kind.FAST_FORWARD, 2, 0, 0);

        assertThat(MergeDialog.summaryText(ff, null, "main", "feature", false))
                .isEqualTo(Messages.format("merge.summary.fastForward", "main", "feature", "2"));
        assertThat(MergeDialog.summaryText(ff, null, "main", "feature", true))
                .isEqualTo(Messages.format("merge.summary.fastForwardCommit", "main", "feature", "2"));
    }

    @Test
    void summaryCoversOtherOutcomes() {
        assertThat(MergeDialog.summaryText(preview(MergePreview.Kind.MERGE_COMMIT, 3, 1, 0), null, "main", "f", false))
                .isEqualTo(Messages.format("merge.summary.mergeCommit", "main", "f", "3", "1"));
        assertThat(MergeDialog.summaryText(preview(MergePreview.Kind.UP_TO_DATE, 0, 1, 0), null, "main", "f", false))
                .isEqualTo(Messages.format("merge.summary.upToDate", "main", "f"));
        assertThat(MergeDialog.summaryText(null, null, "main", "f", false))
                .isEqualTo(Messages.get("merge.summary.loading"));
        assertThat(MergeDialog.summaryText(null, "boom", "main", "f", false))
                .isEqualTo(Messages.format("merge.summary.error", "boom"));
        assertThat(MergeDialog.summaryText(
                preview(MergePreview.Kind.FAST_FORWARD, MergePreview.COUNT_LIMIT, 0, 0), null, "m", "f", false))
                .contains(MergePreview.COUNT_LIMIT + "+");
    }

    @Test
    void conflictLineDependsOnOutcomeAndCheck() {
        MergePreview merge = preview(MergePreview.Kind.MERGE_COMMIT, 2, 1, 0);
        MergeConflicts six = new MergeConflicts(MergeConflicts.Certainty.EXACT,
                List.of("a", "b", "c", "d", "e", "f"));

        assertThat(MergeDialog.conflictsText(merge, null, true)).isEqualTo(Messages.get("merge.conflicts.checking"));
        assertThat(MergeDialog.conflictsText(merge, null, false)).isNull();
        assertThat(MergeDialog.conflictsText(merge, new MergeConflicts(MergeConflicts.Certainty.EXACT, List.of()),
                false)).isEqualTo(Messages.get("merge.conflicts.none"));
        assertThat(MergeDialog.conflictsText(merge, six, false)).isEqualTo(Messages.format("merge.conflicts.exact", 6,
                Messages.format("merge.conflicts.more", "a, b, c, d, e", 1)));
        assertThat(MergeDialog.conflictsText(merge,
                new MergeConflicts(MergeConflicts.Certainty.LIKELY, List.of("src/main/A.java")), false))
                .isEqualTo(Messages.format("merge.conflicts.likely", 1, "A.java"));
        assertThat(MergeDialog.conflictsText(preview(MergePreview.Kind.FAST_FORWARD, 2, 0, 0), null, false))
                .isEqualTo(Messages.get("merge.conflicts.none"));
        assertThat(MergeDialog.conflictsText(preview(MergePreview.Kind.UP_TO_DATE, 0, 0, 0), null, false)).isNull();
    }

    @Test
    void longBranchNamesKeepStartAndEnd() {
        String name = "bugfix/JIRA-12345-implement-very-long-branch-name-for-login-form-validation";

        assertThat(MergeDialog.shortName("main")).isEqualTo("main");
        assertThat(MergeDialog.shortName(name)).hasSize(30).startsWith("bugfix/JIRA-12345").endsWith("validation")
                .contains("…");
    }

    @Test
    void warnsAboutLocalChangesOnlyWhenMergeCanRun() {
        assertThat(MergeDialog.warningText(preview(MergePreview.Kind.MERGE_COMMIT, 1, 1, 2)))
                .isEqualTo(Messages.format("merge.warning.localChanges", 2));
        assertThat(MergeDialog.warningText(preview(MergePreview.Kind.MERGE_COMMIT, 1, 1, 0))).isNull();
        assertThat(MergeDialog.warningText(preview(MergePreview.Kind.UP_TO_DATE, 0, 0, 2))).isNull();
        assertThat(MergeDialog.warningText(null)).isNull();
    }
}
