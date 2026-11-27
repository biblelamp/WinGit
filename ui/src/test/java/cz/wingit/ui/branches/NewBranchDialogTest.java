package cz.wingit.ui.branches;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;

import cz.wingit.core.log.CommitInfo;
import cz.wingit.ui.common.PastedNameFilter;
import cz.wingit.ui.common.RenameDialog;
import cz.wingit.ui.history.CommitDates;

class NewBranchDialogTest {

    private final List<String> existing = List.of("main", "feature/login");

    @Test
    void acceptsNewValidNames() {
        assertThat(NewBranchDialog.problem("", existing)).isNull();
        assertThat(NewBranchDialog.problem("feature/logout", existing)).isNull();
    }

    @Test
    void explainsInvalidAndExistingNames() {
        assertThat(NewBranchDialog.problem("my branch", existing)).startsWith("This is not a valid branch name");
        assertThat(NewBranchDialog.problem("Feature/Login", existing))
                .isEqualTo("A branch named \"Feature/Login\" already exists.");
    }

    @Test
    void renameIgnoresTheBranchItselfSoCaseChangesAreAllowed() {
        List<String> others = RenameDialog.others("feature/login", existing);
        assertThat(others).containsExactly("main");
        assertThat(NewBranchDialog.problem("Feature/Login", others)).isNull();
        assertThat(NewBranchDialog.problem("MAIN", others)).isNotNull();
    }

    @Test
    void pastedTextGetsUnderscoresInsteadOfSpaces() {
        assertThat(PastedNameFilter.name("  Fix login  page\n")).isEqualTo("Fix_login_page");
        assertThat(PastedNameFilter.name("a\tb")).isEqualTo("a_b");
        assertThat(PastedNameFilter.name("feature/x")).isEqualTo("feature/x");
    }

    @Test
    void showsTheCommitTheBranchStartsAt() {
        CommitInfo commit = new CommitInfo("a1b2c3d4e5f6", List.of(), "Fix login page", "Jan",
                Instant.parse("2026-01-02T10:00:00Z"), List.of(), false);
        CommitDates dates = CommitDates.system();
        String when = dates.format(commit.authorTime());

        assertThat(NewBranchDialog.commitLine(commit)).isEqualTo("a1b2c3d  Fix login page");
        assertThat(NewBranchDialog.detailsLine(new NewBranchDialog.Source(commit, "main"), dates))
                .isEqualTo("Jan, " + when + " · branch main");
        assertThat(NewBranchDialog.detailsLine(new NewBranchDialog.Source(commit, null), dates))
                .isEqualTo("Jan, " + when + " · detached HEAD");
    }

    @Test
    void longCommitMessageIsCut() {
        CommitInfo commit = new CommitInfo("a1b2c3d4e5f6", List.of(), "x".repeat(100), "Jan", Instant.EPOCH,
                List.of(), false);
        assertThat(NewBranchDialog.commitLine(commit)).hasSize(7 + 2 + 60).endsWith("…");
    }
}
