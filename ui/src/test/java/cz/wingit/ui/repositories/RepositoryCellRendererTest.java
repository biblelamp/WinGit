package cz.wingit.ui.repositories;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;

import javax.swing.JLabel;
import javax.swing.JList;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

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
    void largeCountsAreCapped() {
        JLabel cell = render(new RepositoryEntry(path, "main", false, false, RepositoriesPane.MAX_UNPUSHED));

        assertThat(cell.getText()).contains("\u2191999+");
    }

    private JLabel render(RepositoryEntry entry) {
        return (JLabel) renderer.getListCellRendererComponent(list, entry, 0, false, false);
    }
}
