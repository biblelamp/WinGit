package cz.wingit.ui.repositories;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;

import javax.swing.Icon;
import javax.swing.JLabel;
import javax.swing.JList;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.formdev.flatlaf.extras.FlatSVGIcon;

import cz.wingit.ui.Theme;

class RepositoryCellRendererTest {

    private final Path path = Path.of("C:/work/GitExercises");
    private final RepositoryCellRenderer renderer = new RepositoryCellRenderer(() -> null);
    private final JList<RepositoryEntry> list = new JList<>();

    @BeforeAll
    static void installTheme() {
        Theme.install(Theme.Mode.LIGHT);
    }

    @Test
    void showsUnpushedCountAfterBranch() {
        JLabel cell = render(new RepositoryEntry(path, "master", false, false, 2));

        assertThat(cell.getText()).contains("GitExercises", "(master)", "\u21912");
        assertThat(cell.getToolTipText()).endsWith("2 commit(s) to push");
    }

    @Test
    void noMarkerWhenEverythingIsPushedOrUnknown() {
        assertThat(render(new RepositoryEntry(path, "master", false, false, 0)).getText()).doesNotContain("\u2191");
        assertThat(render(new RepositoryEntry(path, "master", false, false, null)).getText()).doesNotContain("\u2191");
        assertThat(render(new RepositoryEntry(path, "master", false, false, 0)).getToolTipText())
                .isEqualTo(path.toString());
    }

    @Test
    void showsUnpulledCountNextToUnpushed() {
        JLabel cell = render(new RepositoryEntry(path, "main", false, false, 1, 7));

        assertThat(cell.getText()).contains("(main)", "↑1", "↓7");
        assertThat(cell.getToolTipText()).endsWith("1 commit(s) to push – 7 commit(s) to pull");
        assertThat(render(new RepositoryEntry(path, "main", false, false, 0, 0)).getText()).doesNotContain("↓");
        assertThat(render(new RepositoryEntry(path, "main", false, true, 0, 3)).getText()).doesNotContain("↓");
    }

    @Test
    void localChangesWinOverIncomingCommits() {
        assertThat(iconOf(new RepositoryEntry(path, "main", false, false, 0, 0))).isEqualTo("repository.svg");
        assertThat(iconOf(new RepositoryEntry(path, "main", false, false, 0, 3))).isEqualTo("repository-incoming.svg");
        assertThat(iconOf(new RepositoryEntry(path, "main", true, false, 0, 0))).isEqualTo("repository-changed.svg");
        assertThat(iconOf(new RepositoryEntry(path, "main", true, false, 0, 3))).isEqualTo("repository-changed.svg");
        assertThat(iconOf(new RepositoryEntry(path, null, null, true))).isEqualTo("repository-missing.svg");
    }

    @Test
    void nameIsRedOnlyWithLocalChangesAndNotWhenSelected() {
        assertThat(render(new RepositoryEntry(path, "main", true, false)).getText())
                .contains("<span style='color:#DB5860'>GitExercises</span>");
        assertThat(render(new RepositoryEntry(path, null, true, false)).getText())
                .isEqualTo("<html><span style='color:#DB5860'>GitExercises</span></html>");
        assertThat(render(new RepositoryEntry(path, "main", false, false, 0, 3)).getText()).doesNotContain("#DB5860");
        assertThat(render(new RepositoryEntry(path, null, false, false)).getText()).isEqualTo("GitExercises");
        JLabel selected = (JLabel) renderer.getListCellRendererComponent(
                list, new RepositoryEntry(path, "main", true, false), 0, true, false);
        assertThat(selected.getText()).doesNotContain("#DB5860");
    }

    @Test
    void largeCountsAreCapped() {
        JLabel cell = render(new RepositoryEntry(path, "main", false, false, RepositoriesPane.MAX_UNPUSHED));

        assertThat(cell.getText()).contains("\u2191999+");
    }

    private String iconOf(RepositoryEntry entry) {
        Icon icon = render(entry).getIcon();
        String name = ((FlatSVGIcon) icon).getName();
        return name.substring(name.lastIndexOf('/') + 1);
    }

    private JLabel render(RepositoryEntry entry) {
        return (JLabel) renderer.getListCellRendererComponent(list, entry, 0, false, false);
    }
}
