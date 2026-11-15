package cz.wingit.core.diff;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import cz.wingit.core.diff.DiffRow.Type;

class SideBySideTest {

    @Test
    void identicalTextsAreAllSame() {
        assertThat(SideBySide.compute("a\nb\n", "a\nb\n")).containsExactly(
                new DiffRow(Type.SAME, 1, "a", 1, "a"),
                new DiffRow(Type.SAME, 2, "b", 2, "b"));
    }

    @Test
    void insertedAndDeletedLinesGetFillers() {
        assertThat(SideBySide.compute("a\nc\n", "a\nb\nc\n")).containsExactly(
                new DiffRow(Type.SAME, 1, "a", 1, "a"),
                new DiffRow(Type.ADDED, 0, null, 2, "b"),
                new DiffRow(Type.SAME, 2, "c", 3, "c"));

        assertThat(SideBySide.compute("a\nb\nc\n", "a\nc\n")).containsExactly(
                new DiffRow(Type.SAME, 1, "a", 1, "a"),
                new DiffRow(Type.DELETED, 2, "b", 0, null),
                new DiffRow(Type.SAME, 3, "c", 2, "c"));
    }

    @Test
    void replacedBlockPairsLinesThenAddsTheRest() {
        assertThat(SideBySide.compute("x\n1\n2\ny\n", "x\nA\nB\nC\ny\n")).containsExactly(
                new DiffRow(Type.SAME, 1, "x", 1, "x"),
                new DiffRow(Type.CHANGED, 2, "1", 2, "A"),
                new DiffRow(Type.CHANGED, 3, "2", 3, "B"),
                new DiffRow(Type.ADDED, 0, null, 4, "C"),
                new DiffRow(Type.SAME, 4, "y", 5, "y"));
    }

    @Test
    void newAndDeletedFiles() {
        assertThat(SideBySide.compute("", "one\ntwo")).extracting(DiffRow::type).containsExactly(Type.ADDED, Type.ADDED);
        assertThat(SideBySide.compute("one\n", "")).extracting(DiffRow::type).containsExactly(Type.DELETED);
        assertThat(SideBySide.compute("", "")).isEmpty();
    }

    @Test
    void changedLineEndingIsAChangeButCrIsNotShown() {
        List<DiffRow> rows = SideBySide.compute("a\nb\n", "a\r\nb\n");

        assertThat(rows.get(0)).isEqualTo(new DiffRow(Type.CHANGED, 1, "a", 1, "a"));
        assertThat(rows.get(1).type()).isEqualTo(Type.SAME);
    }
}
