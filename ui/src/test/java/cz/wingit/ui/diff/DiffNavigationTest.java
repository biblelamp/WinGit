package cz.wingit.ui.diff;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;

import javax.swing.SwingUtilities;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import cz.wingit.core.diff.DiffRow;
import cz.wingit.core.diff.FileDiff;
import cz.wingit.core.diff.FileVersion;
import cz.wingit.ui.Theme;

class DiffNavigationTest {

    @BeforeAll
    static void installTheme() {
        Theme.install(Theme.Mode.LIGHT);
    }

    private static DiffRow same(int line) {
        return new DiffRow(DiffRow.Type.SAME, line, "x", line, "x");
    }

    private static DiffRow changed(int line) {
        return new DiffRow(DiffRow.Type.CHANGED, line, "a", line, "b");
    }

    /** Rows 0-99 the same except fragments at 10-11 and 50, and an added row at 80. */
    private static List<DiffRow> rows() {
        List<DiffRow> rows = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            if (i == 10 || i == 11 || i == 50) {
                rows.add(changed(i + 1));
            } else if (i == 80) {
                rows.add(new DiffRow(DiffRow.Type.ADDED, 0, null, i + 1, "new"));
            } else {
                rows.add(same(i + 1));
            }
        }
        return rows;
    }

    @Test
    void fragmentsStartWhereChangedRowsBegin() {
        List<Integer> starts = DiffChanges.starts(rows());

        assertThat(starts).containsExactly(10, 50, 80);
        assertThat(DiffChanges.next(starts, -1)).isZero();
        assertThat(DiffChanges.next(starts, 10)).isEqualTo(1);
        assertThat(DiffChanges.next(starts, 80)).isEqualTo(-1);
        assertThat(DiffChanges.previous(starts, 50)).isZero();
        assertThat(DiffChanges.previous(starts, 10)).isEqualTo(-1);
        assertThat(DiffChanges.starts(List.of(same(1), same(2)))).isEmpty();
    }

    @Test
    void overviewMarksEachFragmentByWhatItDoes() {
        assertThat(DiffChanges.fragments(rows())).containsExactly(
                new DiffChanges.Fragment(10, 12, true, true),
                new DiffChanges.Fragment(50, 51, true, true),
                new DiffChanges.Fragment(80, 81, false, true));
        assertThat(DiffChanges.fragments(List.of(new DiffRow(DiffRow.Type.DELETED, 1, "x", 0, null))))
                .containsExactly(new DiffChanges.Fragment(0, 1, true, false));

        assertThat(DiffOverview.rowAt(0, 200, 100)).isZero();
        assertThat(DiffOverview.rowAt(100, 200, 100)).isEqualTo(50);
        assertThat(DiffOverview.rowAt(250, 200, 100)).isEqualTo(99);
    }

    @Test
    void changedLinesGetTheirChangedWordsHighlighted() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            DiffView view = new DiffView();
            FileVersion left = new FileVersion(FileVersion.Source.INDEX, true, "", false, false);
            FileVersion right = new FileVersion(FileVersion.Source.WORKING_TREE, true, "", false, false);
            view.showDiff(new FileDiff("a.txt", left, right, rows()));

            assertThat(view.left().changedWordCount()).isEqualTo(3); // "a" on each of the 3 changed rows
            assertThat(view.right().changedWordCount()).isEqualTo(3);
            assertThat(view.overview().fragments()).hasSize(3);

            view.showMessage("no file");
            assertThat(view.overview().fragments()).isEmpty();
        });
    }

    @Test
    void opensAtTheFirstChangeAndWalksThroughTheFragments() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            DiffView view = new DiffView();
            FileVersion left = new FileVersion(FileVersion.Source.INDEX, true, "", false, false);
            FileVersion right = new FileVersion(FileVersion.Source.WORKING_TREE, true, "", false, false);
            view.showDiff(new FileDiff("a.txt", left, right, rows()));

            assertThat(view.currentChangeRow()).isEqualTo(10);
            assertThat(view.changesText()).isEqualTo("1 of 3");
            assertThat(view.hasPreviousChange()).isFalse();
            assertThat(view.hasNextChange()).isTrue();

            view.nextChange();
            view.nextChange();
            assertThat(view.currentChangeRow()).isEqualTo(80);
            assertThat(view.changesText()).isEqualTo("3 of 3");
            assertThat(view.hasNextChange()).isFalse();

            view.previousChange();
            assertThat(view.currentChangeRow()).isEqualTo(50);
            assertThat(view.hasPreviousChange()).isTrue();

            view.showMessage("no file");
            assertThat(view.changesText()).isEmpty();
            assertThat(view.hasNextChange()).isFalse();
        });
    }
}
