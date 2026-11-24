package cz.wingit.core.diff;

import java.util.ArrayList;
import java.util.List;

import com.github.difflib.DiffUtils;
import com.github.difflib.patch.AbstractDelta;
import com.github.difflib.patch.Chunk;

/**
 * Changed fragments within a pair of changed lines (pure function), compared word by word: a word is a
 * run of letters, digits and {@code _}, a run of white space is one token, any other character is a token
 * of its own. Each side gets the character ranges that are not in the other line.
 */
public final class InlineDiff {

    /** Lines longer than this are not compared word by word: the whole line counts as changed. */
    static final int MAX_LENGTH = 10_000;

    /** Characters {@code start} (inclusive) to {@code end} (exclusive) of a line. */
    public record Range(int start, int end) {
    }

    /** Changed ranges of the left and of the right line, in order, not touching each other. */
    public record Result(List<Range> left, List<Range> right) {
    }

    private InlineDiff() {
    }

    public static Result compute(String left, String right) {
        if (left.length() > MAX_LENGTH || right.length() > MAX_LENGTH) {
            return new Result(whole(left), whole(right));
        }
        List<String> a = tokens(left);
        List<String> b = tokens(right);
        int[] aOffsets = offsets(a);
        int[] bOffsets = offsets(b);
        List<Range> leftRanges = new ArrayList<>();
        List<Range> rightRanges = new ArrayList<>();
        for (AbstractDelta<String> delta : DiffUtils.diff(a, b).getDeltas()) {
            add(leftRanges, delta.getSource(), aOffsets);
            add(rightRanges, delta.getTarget(), bOffsets);
        }
        return new Result(leftRanges, rightRanges);
    }

    /** Splits a line into words, white-space runs and single other characters. */
    static List<String> tokens(String line) {
        List<String> tokens = new ArrayList<>();
        int i = 0;
        while (i < line.length()) {
            int kind = kind(line.charAt(i));
            int end = i + 1;
            if (kind != OTHER) {
                while (end < line.length() && kind(line.charAt(end)) == kind) {
                    end++;
                }
            }
            tokens.add(line.substring(i, end));
            i = end;
        }
        return tokens;
    }

    private static final int WORD = 0;
    private static final int SPACE = 1;
    private static final int OTHER = 2;

    private static int kind(char c) {
        if (Character.isLetterOrDigit(c) || c == '_') {
            return WORD;
        }
        return Character.isWhitespace(c) ? SPACE : OTHER;
    }

    /** Character offset where each token starts, plus the total length at the end. */
    private static int[] offsets(List<String> tokens) {
        int[] offsets = new int[tokens.size() + 1];
        for (int i = 0; i < tokens.size(); i++) {
            offsets[i + 1] = offsets[i] + tokens.get(i).length();
        }
        return offsets;
    }

    private static void add(List<Range> ranges, Chunk<String> chunk, int[] offsets) {
        if (chunk.size() == 0) {
            return; // nothing of this side: the other side only has an insertion here
        }
        int start = offsets[chunk.getPosition()];
        int end = offsets[chunk.getPosition() + chunk.size()];
        if (!ranges.isEmpty() && ranges.get(ranges.size() - 1).end() == start) {
            start = ranges.remove(ranges.size() - 1).start();
        }
        ranges.add(new Range(start, end));
    }

    private static List<Range> whole(String line) {
        return line.isEmpty() ? List.of() : List.of(new Range(0, line.length()));
    }
}
