package cz.wingit.ui.diff;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Rectangle;
import java.awt.geom.Rectangle2D;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.text.BadLocationException;
import javax.swing.text.DefaultHighlighter;
import javax.swing.text.Highlighter;

import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;
import org.fife.ui.rsyntaxtextarea.SyntaxConstants;
import org.fife.ui.rtextarea.LineNumberFormatter;
import org.fife.ui.rtextarea.RTextScrollPane;

import cz.wingit.core.diff.DiffRow;
import cz.wingit.core.diff.InlineDiff;
import cz.wingit.ui.Theme;

/**
 * One side of the side-by-side view: caption, read-only text with syntax highlighting, change
 * highlights and the real line numbers of this side (filler lines have none).
 */
final class DiffSide extends JPanel {

    private static final long serialVersionUID = 1L;

    // Line backgrounds (translucent, so they work on the light and the dark theme): a deleted or added line
    // in full color, a changed line lighter, with its changed words (InlineDiff) in full color on top.
    static final Color DELETED = new Color(0xE8, 0x5C, 0x64, 90); // about #F7C5C8 on a white background
    static final Color ADDED = new Color(0x5C, 0xC8, 0x5C, 90); // about #C5ECC5 on a white background
    static final Color CHANGED_LEFT = new Color(0xE8, 0x5C, 0x64, 36);
    static final Color CHANGED_RIGHT = new Color(0x5C, 0xC8, 0x5C, 40);
    static final Color CHANGED_WORDS_LEFT = new Color(0xE8, 0x5C, 0x64, 80);
    static final Color CHANGED_WORDS_RIGHT = new Color(0x5C, 0xC8, 0x5C, 85);
    static final Color FILLER = new Color(128, 128, 128, 28);
    static final Color MATCH = new Color(0xFF, 0xC8, 0x00, 110);
    static final Color CURRENT_MATCH = new Color(0xFF, 0x80, 0x00, 190);
    private static final Highlighter.HighlightPainter MATCH_PAINTER =
            new DefaultHighlighter.DefaultHighlightPainter(MATCH);
    private static final Highlighter.HighlightPainter CURRENT_MATCH_PAINTER =
            new DefaultHighlighter.DefaultHighlightPainter(CURRENT_MATCH);

    private static final Highlighter.HighlightPainter CHANGED_WORDS_LEFT_PAINTER =
            new DefaultHighlighter.DefaultHighlightPainter(CHANGED_WORDS_LEFT);
    private static final Highlighter.HighlightPainter CHANGED_WORDS_RIGHT_PAINTER =
            new DefaultHighlighter.DefaultHighlightPainter(CHANGED_WORDS_RIGHT);

    private static final String DARK_SCHEME = "/org/fife/ui/rsyntaxtextarea/themes/dark.xml";

    private final boolean leftSide;
    private final JLabel caption = new JLabel(" ");
    private final RSyntaxTextArea area = new RSyntaxTextArea();
    private final RTextScrollPane scroll = new RTextScrollPane(area);
    /** Real line number per visual line (index = visual line - 1); 0 for fillers. */
    private int[] lineNumbers = new int[0];
    private int maxLineNumber;
    /** Highlights of the search matches on this side. */
    private final List<Object> matchHighlights = new ArrayList<>();
    /** Highlights of the changed words of changed lines on this side. */
    private final List<Object> wordHighlights = new ArrayList<>();

    DiffSide(boolean leftSide) {
        super(new BorderLayout());
        this.leftSide = leftSide;
        if (Theme.current() == Theme.Mode.DARK) {
            applyDarkColors();
        }
        area.setEditable(false);
        area.setHighlightCurrentLine(false);
        area.setCodeFoldingEnabled(false);
        area.setCaretColor(new Color(0, 0, 0, 0)); // read-only: no blinking caret, selection still works
        area.setSyntaxEditingStyle(SyntaxConstants.SYNTAX_STYLE_NONE);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.getGutter().setLineNumberFormatter(new LineNumberFormatter() {
            @Override
            public String format(int lineNumber) {
                int index = lineNumber - 1;
                return index >= 0 && index < lineNumbers.length && lineNumbers[index] > 0
                        ? Integer.toString(lineNumbers[index]) : "";
            }

            @Override
            public int getMaxLength(int maxLineNumberInDocument) {
                return Math.max(2, Integer.toString(Math.max(1, maxLineNumber)).length());
            }
        });
        caption.putClientProperty("FlatLaf.styleClass", "small");
        caption.setBorder(BorderFactory.createEmptyBorder(2, 6, 2, 6));
        add(caption, BorderLayout.NORTH);
        add(scroll, BorderLayout.CENTER);
    }

    /** RSyntaxTextArea's own dark scheme (text, syntax colors, gutter); the scroll pane must exist already. */
    private void applyDarkColors() {
        try (InputStream in = RSyntaxTextArea.class.getResourceAsStream(DARK_SCHEME)) {
            if (in != null) {
                org.fife.ui.rsyntaxtextarea.Theme.load(in, area.getFont()).apply(area);
            }
        } catch (IOException e) {
            // keep the default colors; the diff stays readable
        }
    }

    RTextScrollPane scrollPane() {
        return scroll;
    }

    RSyntaxTextArea textArea() {
        return area;
    }

    void setCaption(String text) {
        caption.setText(text);
    }

    /**
     * Shows this side of the rows; keeps the scroll position when the same file is refreshed.
     *
     * @param changedWords per row, the changed words of a {@link DiffRow.Type#CHANGED} row, else null
     */
    void setRows(List<DiffRow> rows, List<InlineDiff.Result> changedWords, String syntaxStyle) {
        StringBuilder text = new StringBuilder();
        int[] numbers = new int[rows.size()];
        int max = 0;
        for (int i = 0; i < rows.size(); i++) {
            DiffRow row = rows.get(i);
            String line = leftSide ? row.leftText() : row.rightText();
            numbers[i] = leftSide ? row.leftLine() : row.rightLine();
            max = Math.max(max, numbers[i]);
            if (i > 0) {
                text.append('\n');
            }
            text.append(line == null ? "" : line);
        }
        lineNumbers = numbers;
        maxLineNumber = max;
        clearMatches();
        clearHighlights(wordHighlights);

        area.setSyntaxEditingStyle(syntaxStyle);
        area.removeAllLineHighlights();
        area.setText(text.toString());
        area.setCaretPosition(0);
        for (int i = 0; i < rows.size(); i++) {
            Color color = highlight(rows.get(i));
            if (color != null) {
                try {
                    area.addLineHighlight(i, color);
                } catch (BadLocationException e) {
                    // cannot happen: the text has exactly rows.size() lines
                }
            }
            InlineDiff.Result words = changedWords.get(i);
            if (words != null) {
                highlightWords(i, leftSide ? words.left() : words.right());
            }
        }
        scroll.getGutter().repaint();
    }

    private void highlightWords(int row, List<InlineDiff.Range> ranges) {
        Highlighter.HighlightPainter painter = leftSide ? CHANGED_WORDS_LEFT_PAINTER : CHANGED_WORDS_RIGHT_PAINTER;
        try {
            int offset = area.getLineStartOffset(row);
            for (InlineDiff.Range range : ranges) {
                wordHighlights.add(area.getHighlighter().addHighlight(offset + range.start(),
                        offset + range.end(), painter));
            }
        } catch (BadLocationException e) {
            // cannot happen: the ranges are within the line of this row
        }
    }

    /** Number of changed-word highlights shown (tests). */
    int changedWordCount() {
        return wordHighlights.size();
    }

    private Color highlight(DiffRow row) {
        switch (row.type()) {
            case CHANGED:
                return leftSide ? CHANGED_LEFT : CHANGED_RIGHT;
            case DELETED:
                return leftSide ? DELETED : FILLER;
            case ADDED:
                return leftSide ? FILLER : ADDED;
            case SAME:
            default:
                return null;
        }
    }

    /** Highlights the matches of this side; {@code current} (of either side, or null) stands out. */
    void showMatches(List<DiffSearch.Match> matches, DiffSearch.Match current) {
        clearMatches();
        for (DiffSearch.Match match : matches) {
            if (match.left() != leftSide) {
                continue;
            }
            try {
                int offset = area.getLineStartOffset(match.row());
                matchHighlights.add(area.getHighlighter().addHighlight(offset + match.start(),
                        offset + match.end(), match.equals(current) ? CURRENT_MATCH_PAINTER : MATCH_PAINTER));
            } catch (BadLocationException e) {
                // the match belongs to rows not shown (any more); skip it
            }
        }
    }

    private void clearMatches() {
        clearHighlights(matchHighlights);
    }

    private void clearHighlights(List<Object> highlights) {
        for (Object highlight : highlights) {
            area.getHighlighter().removeHighlight(highlight);
        }
        highlights.clear();
    }

    /** Scrolls a match of this side into view, with {@code contextLines} lines above and below it. */
    void scrollToMatch(DiffSearch.Match match, int contextLines) {
        try {
            int offset = area.getLineStartOffset(match.row());
            Rectangle2D start = area.modelToView2D(offset + match.start());
            Rectangle2D end = area.modelToView2D(offset + match.end());
            if (start == null || end == null) {
                return; // not laid out yet
            }
            Rectangle bounds = start.createUnion(end).getBounds();
            int context = contextLines * area.getLineHeight();
            bounds.y -= context;
            bounds.height += 2 * context;
            area.scrollRectToVisible(bounds);
        } catch (BadLocationException e) {
            // the match belongs to rows not shown (any more)
        }
    }

    /** Real line number shown in the gutter for a visual line (1-based), "" for fillers (tests). */
    String gutterText(int visualLine) {
        return scroll.getGutter().getLineNumberFormatter().format(visualLine);
    }
}
