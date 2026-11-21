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
    void incomingCommitsWinOverLocalChanges() {
        assertThat(iconOf(new RepositoryEntry(path, "main", false, false, 0, 0))).isEqualTo("repository.svg");
        assertThat(iconOf(new RepositoryEntry(path, "main", false, false, 0, 3))).isEqualTo("repository-incoming.svg");
        assertThat(iconOf(new RepositoryEntry(path, "main", true, false, 0, 0))).isEqualTo("repository-changed.svg");
        assertThat(iconOf(new RepositoryEntry(path, "main", true, false, 0, 3))).isEqualTo("repository-incoming.svg");
        assertThat(iconOf(new RepositoryEntry(path, null, null, true))).isEqualTo("repository-missing.svg");
    }

    @Test
    void localChangesColorOnlyTheIcon() {
        JLabel cell = render(new RepositoryEntry(path, "main", true, false));

        assertThat(cell.getText()).doesNotContain("#DB5860").startsWith("<html>GitExercises <span");
        assertThat(iconOf(new RepositoryEntry(path, "main", true, false))).isEqualTo("repository-changed.svg");
    }

    @Test
    void onlyTheNameOfTheOpenedRepositoryIsBold() {
        RepositoryCellRenderer openedRenderer = new RepositoryCellRenderer(() -> path);
        JLabel cell = (JLabel) openedRenderer.getListCellRendererComponent(
                list, new RepositoryEntry(path, "main", false, false), 0, false, false);

        assertThat(cell.getFont().isBold()).isFalse();
        assertThat(cell.getText()).startsWith("<html><b>GitExercises</b> <span").contains("(main)</span>");
        assertThat(render(new RepositoryEntry(path, "main", false, false)).getText()).doesNotContain("<b>");
    }

    @Test
    void selectedRowHasLightBackgroundAndKeepsTextColors() {
        JLabel selected = (JLabel) renderer.getListCellRendererComponent(
                list, new RepositoryEntry(path, "main", true, false), 0, true, true);

        assertThat(selected.getBackground()).isIn(Theme.SELECTION_LIGHT, Theme.SELECTION_INACTIVE_LIGHT);
        assertThat(selected.getForeground()).isEqualTo(list.getForeground());
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
