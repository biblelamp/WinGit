package cz.wingit.core.diff;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import cz.wingit.core.diff.InlineDiff.Range;

class InlineDiffTest {

    @Test
    void splitsIntoWordsSpacesAndOtherCharacters() {
        assertThat(InlineDiff.tokens("int  count_2=a.b();"))
                .containsExactly("int", "  ", "count_2", "=", "a", ".", "b", "(", ")", ";");
        assertThat(InlineDiff.tokens("")).isEmpty();
    }

    @Test
    void marksChangedWordsOnEachSide() {
        InlineDiff.Result result = InlineDiff.compute("int count = 1;", "int total = 1;");
        assertThat(result.left()).containsExactly(new Range(4, 9));
        assertThat(result.right()).containsExactly(new Range(4, 9));
    }

    @Test
    void anInsertionMarksOnlyTheSideThatHasIt() {
        InlineDiff.Result result = InlineDiff.compute("call(a);", "call(a, b);");
        assertThat(result.left()).isEmpty();
        assertThat(result.right()).containsExactly(new Range(6, 9)); // ", b"
    }

    @Test
    void adjacentChangesBecomeOneRangeAndSeparateOnesStayApart() {
        InlineDiff.Result result = InlineDiff.compute("a.b x c", "a->b x d");
        assertThat(result.left()).containsExactly(new Range(1, 2), new Range(6, 7));
        assertThat(result.right()).containsExactly(new Range(1, 3), new Range(7, 8));
    }

    @Test
    void veryLongLinesCountAsWhollyChanged() {
        String longLine = "x".repeat(InlineDiff.MAX_LENGTH + 1);
        InlineDiff.Result result = InlineDiff.compute(longLine, "y");
        assertThat(result.left()).containsExactly(new Range(0, longLine.length()));
        assertThat(result.right()).isEqualTo(List.of(new Range(0, 1)));
    }
}
