package cz.wingit.ui.history;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.junit.jupiter.api.Test;

import cz.wingit.core.graph.GraphRow;
import cz.wingit.core.graph.LaneLayout;
import cz.wingit.core.log.CommitInfo;
import cz.wingit.core.log.RefLabel;

class CommitTableModelTest {

    private final CommitTableModel model = new CommitTableModel(
            new CommitDates(Clock.fixed(Instant.EPOCH, ZoneId.of("UTC")), Locale.UK));

    @Test
    void filterMatchesMessageAuthorIdPrefixAndRefNames() {
        model.setData(HistoryData.of(List.of(
                commit("c3", "b2", "Fix login bug", "Jana", List.of()),
                commit("b2", "a1", "Add README", "Petr", List.of(new RefLabel("v1.0", RefLabel.Kind.TAG, false))),
                commit("a1", null, "Initial", "Jana", List.of()))));

        assertThat(filtered("LOGIN")).containsExactly("Fix login bug");
        assertThat(filtered("jana")).containsExactly("Fix login bug", "Initial");
        assertThat(filtered("b2")).containsExactly("Add README"); // id prefix
        assertThat(filtered("2")).isEmpty(); // ids match only from the start
        assertThat(filtered("v1.")).containsExactly("Add README"); // tag name
        assertThat(model.totalCount()).isEqualTo(3);

        assertThat(model.setFilter("  ")).isTrue(); // blank: everything again
        assertThat(model.isFiltered()).isFalse();
        assertThat(model.getRowCount()).isEqualTo(3);
        assertThat(model.setFilter("")).isFalse(); // no change
    }

    @Test
    void filteredRowsHaveOnlyANodeAndLaterPortionsAreFilteredToo() {
        List<CommitInfo> first = List.of(commit("d4", "c3", "Merge feature", "Jana", List.of()),
                commit("c3", "b2", "Feature work", "Petr", List.of()));
        List<CommitInfo> older = List.of(commit("b2", "a1", "More feature", "Petr", List.of()),
                commit("a1", null, "Initial", "Jana", List.of()));
        LaneLayout layout = new LaneLayout();
        model.setData(new HistoryData(first, layout.append(first)));
        model.setFilter("feature");
        assertThat(model.getRowCount()).isEqualTo(2);
        assertThat(model.getGraphRow(0)).isEqualTo(new GraphRow(0, List.of(), List.of(), 1));
        assertThat(model.maxLaneCount()).isEqualTo(1);

        model.append(new HistoryData(older, layout.append(older)));

        assertThat(rows()).containsExactly("Merge feature", "Feature work", "More feature");
        assertThat(model.totalCount()).isEqualTo(4);
        model.setFilter(null);
        assertThat(model.getGraphRow(1).upper()).isNotEmpty(); // the real graph is back
    }

    private List<String> filtered(String text) {
        model.setFilter(text);
        return rows();
    }

    private List<String> rows() {
        List<String> rows = new ArrayList<>();
        for (int row = 0; row < model.getRowCount(); row++) {
            rows.add(model.getCommit(row).message());
        }
        return rows;
    }

    private static CommitInfo commit(String id, String parent, String message, String author, List<RefLabel> refs) {
        return new CommitInfo(id, parent == null ? List.of() : List.of(parent), message, author, Instant.EPOCH,
                refs, false);
    }
}
