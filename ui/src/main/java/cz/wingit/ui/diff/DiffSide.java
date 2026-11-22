package cz.wingit.ui.diff;

import java.awt.BorderLayout;
import java.awt.Color;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.text.BadLocationException;

import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;
import org.fife.ui.rsyntaxtextarea.SyntaxConstants;
import org.fife.ui.rtextarea.LineNumberFormatter;
import org.fife.ui.rtextarea.RTextScrollPane;

import cz.wingit.core.diff.DiffRow;
import cz.wingit.ui.Theme;

/**
 * One side of the side-by-side view: caption, read-only text with syntax highlighting, change
 * highlights and the real line numbers of this side (filler lines have none).
 */
final class DiffSide extends JPanel {

    private static final long serialVersionUID = 1L;

    static final Color DELETED = new Color(0xDB, 0x58, 0x60, 56);
    static final Color ADDED = new Color(0x59, 0xA8, 0x69, 56);
    static final Color CHANGED = new Color(0xED, 0xA2, 0x00, 48);
    static final Color FILLER = new Color(128, 128, 128, 28);

    private static final String DARK_SCHEME = "/org/fife/ui/rsyntaxtextarea/themes/dark.xml";

    private final boolean leftSide;
    private final JLabel caption = new JLabel(" ");
    private final RSyntaxTextArea area = new RSyntaxTextArea();
    private final RTextScrollPane scroll = new RTextScrollPane(area);
    /** Real line number per visual line (index = visual line - 1); 0 for fillers. */
    private int[] lineNumbers = new int[0];
    private int maxLineNumber;

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

    /** Shows this side of the rows; keeps the scroll position when the same file is refreshed. */
    void setRows(List<DiffRow> rows, String syntaxStyle) {
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
        }
        scroll.getGutter().repaint();
    }

    private Color highlight(DiffRow row) {
        switch (row.type()) {
            case CHANGED:
                return CHANGED;
            case DELETED:
                return leftSide ? DELETED : FILLER;
            case ADDED:
                return leftSide ? FILLER : ADDED;
            case SAME:
            default:
                return null;
        }
    }

    /** Real line number shown in the gutter for a visual line (1-based), "" for fillers (tests). */
    String gutterText(int visualLine) {
        return scroll.getGutter().getLineNumberFormatter().format(visualLine);
    }
}
