package cz.wingit.ui.branches;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import cz.wingit.ui.common.RenameDialog;

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
}
