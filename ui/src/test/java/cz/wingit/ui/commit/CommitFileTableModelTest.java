package cz.wingit.ui.commit;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import cz.wingit.core.status.ChangeType;
import cz.wingit.core.status.FileChange;

class CommitFileTableModelTest {

    private final FileChange readme = new FileChange("readme.md", null, ChangeType.MODIFIED);
    private final FileChange added = new FileChange("src/New.java", null, ChangeType.UNTRACKED);
    private final FileChange removed = new FileChange("old.txt", ChangeType.DELETED, null);

    @Test
    void includesAllFilesWithoutPreselection() {
        CommitFileTableModel model = new CommitFileTableModel(List.of(readme, added, removed), Set.of());

        assertThat(model.includedChanges()).containsExactly(readme, added, removed);
        assertThat(model.getValueAt(1, CommitFileTableModel.NAME)).isEqualTo("New.java");
        assertThat(model.getValueAt(1, CommitFileTableModel.STATUS)).isEqualTo("Untracked");
        assertThat(model.getValueAt(2, CommitFileTableModel.STATUS)).isEqualTo("Deleted (staged)");
    }

    @Test
    void includesOnlyPreselectedFilesAndTogglesCheckBoxes() {
        CommitFileTableModel model = new CommitFileTableModel(List.of(readme, added, removed), Set.of(added));
        assertThat(model.includedChanges()).containsExactly(added);

        model.setValueAt(true, 0, CommitFileTableModel.INCLUDE);
        model.setValueAt(false, 1, CommitFileTableModel.INCLUDE);
        assertThat(model.includedChanges()).containsExactly(readme);

        model.setAllIncluded(true);
        assertThat(model.includedChanges()).hasSize(3);
        model.setAllIncluded(false);
        assertThat(model.includedChanges()).isEmpty();
        assertThat(model.isCellEditable(0, CommitFileTableModel.NAME)).isFalse();
        assertThat(model.getColumnClass(CommitFileTableModel.INCLUDE)).isEqualTo(Boolean.class);
    }
}
