package cz.wingit.ui.diff;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import javax.swing.SwingUtilities;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import cz.wingit.core.diff.DiffRow;
import cz.wingit.core.diff.FileDiff;
import cz.wingit.core.diff.FileVersion;
import cz.wingit.ui.Theme;

class DiffSearchTest {

    private static final List<DiffRow> ROWS = List.of(
            new DiffRow(DiffRow.Type.SAME, 1, "int foo = 1;", 1, "int foo = 1;"),
            new DiffRow(DiffRow.Type.CHANGED, 2, "Foo foofoo", 2, "bar"),
            new DiffRow(DiffRow.Type.ADDED, 0, null, 3, "FOO"));

    @BeforeAll
    static void installTheme() {
        Theme.install(Theme.Mode.LIGHT);
    }

    @Test
    void findsMatchesOnBothSidesInReadingOrder() {
        assertThat(DiffSearch.find(ROWS, "foo", true)).containsExactly(
                new DiffSearch.Match(0, true, 4, 7),
                new DiffSearch.Match(0, false, 4, 7),
                new DiffSearch.Match(1, true, 4, 7),
                new DiffSearch.Match(1, true, 7, 10));
        assertThat(DiffSearch.find(ROWS, "foo", false)).hasSize(6)
                .contains(new DiffSearch.Match(1, true, 0, 3), new DiffSearch.Match(2, false, 0, 3));
        assertThat(DiffSearch.find(ROWS, "", false)).isEmpty();
        assertThat(DiffSearch.find(ROWS, "missing", false)).isEmpty();
    }

    @Test
    void firstMatchFromARowWrapsAround() {
        List<DiffSearch.Match> matches = DiffSearch.find(ROWS, "foo", false);
        assertThat(DiffSearch.firstFrom(matches, 0)).isZero();
        assertThat(DiffSearch.firstFrom(matches, 1)).isEqualTo(2);
        assertThat(DiffSearch.firstFrom(matches, 5)).isZero();
        assertThat(DiffSearch.firstFrom(List.of(), 0)).isEqualTo(-1);
    }

    @Test
    void viewWalksThroughMatchesAndCountsThem() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            DiffView view = new DiffView();
            FileVersion left = new FileVersion(FileVersion.Source.INDEX, true, "", false, false);
            FileVersion right = new FileVersion(FileVersion.Source.WORKING_TREE, true, "", false, false);
            view.showDiff(new FileDiff("a.txt", left, right, ROWS));
            assertThat(view.matchesText()).isEmpty();

            view.search("foo", true);
            assertThat(view.matchesText()).isEqualTo("1 of 4");
            view.previousMatch();
            assertThat(view.currentMatch()).isEqualTo(new DiffSearch.Match(1, true, 7, 10));
            view.nextMatch();
            assertThat(view.matchesText()).isEqualTo("1 of 4");

            view.search("nothing", false);
            assertThat(view.matchesText()).isEqualTo("No matches");
            view.nextMatch();
            assertThat(view.currentMatch()).isNull();

            view.search("", false);
            assertThat(view.matchesText()).isEmpty();
        });
    }

    @Test
    void searchInTheWindowHeaderIsAlwaysShownAndEscClearsItFirst() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            DiffView view = new DiffView();
            DiffSearchBar bar = new DiffSearchBar(view, true);
            assertThat(bar.isVisible()).isTrue();
            javax.swing.Action escape = bar.field().getActionMap().get("escape");
            assertThat(escape.isEnabled()).isFalse(); // empty: Esc goes on to the window, which closes

            bar.field().setText("foo");
            assertThat(escape.isEnabled()).isTrue();
            escape.actionPerformed(null);
            assertThat(bar.field().getText()).isEmpty();
            bar.close();
            assertThat(bar.isVisible()).isTrue();
        });
    }
}
