package cz.wingit.ui.diff;

import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.GridBagLayout;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollBar;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;

import org.fife.ui.rsyntaxtextarea.FileTypeUtil;
import org.fife.ui.rsyntaxtextarea.SyntaxConstants;

import cz.wingit.core.diff.DiffReader;
import cz.wingit.core.diff.DiffRow;
import cz.wingit.core.diff.InlineDiff;
import cz.wingit.core.diff.FileDiff;
import cz.wingit.core.diff.FileVersion;
import cz.wingit.ui.Messages;

/**
 * Side-by-side display of a {@link FileDiff}, or a centered message (no file, loading, binary...).
 * Both sides scroll vertically together; their lines are aligned by filler lines. "Next/previous change"
 * scroll a changed fragment to the top, with a few lines of context above it.
 */
final class DiffView extends JPanel {

    private static final long serialVersionUID = 1L;
    private static final String MESSAGE = "message";
    private static final String DIFF = "diff";

    private final CardLayout cards = new CardLayout();
    private final JLabel message = new JLabel();
    private final DiffSide left = new DiffSide(true);
    private final DiffSide right = new DiffSide(false);
    /** Marks of the changes along the right edge, like a map of the whole file. */
    private final DiffOverview overview = new DiffOverview(this::scrollToRow);
    private String shownPath;

    /** Lines of context shown above a fragment that "next/previous change" scrolled to. */
    static final int CONTEXT_LINES = 3;
    /** First row of each changed fragment of the shown diff. */
    private List<Integer> changes = List.of();
    /** Fragment the user navigated to last, -1 for none. */
    private int current = -1;
    /** Top line after that navigation; if the view is scrolled elsewhere, navigation starts from there. */
    private int navigatedTop = -1;
    private final List<Runnable> navigationListeners = new ArrayList<>();
    /** Rows of the shown diff, for the search. */
    private List<DiffRow> rows = List.of();
    private String query = "";
    private boolean matchCase;
    private List<DiffSearch.Match> matches = List.of();
    /** Match the user went to last (or the search jumped to), -1 for none. */
    private int currentMatch = -1;

    DiffView() {
        setLayout(cards);

        JPanel messagePanel = new JPanel(new GridBagLayout());
        message.setForeground(UIManager.getColor("Label.disabledForeground"));
        messagePanel.add(message);
        add(messagePanel, MESSAGE);

        add(new HalfSplitPane(left, right), DIFF);
        right.add(overview, BorderLayout.EAST); // beside the scroll pane, below the caption

        // aligned sides have the same number of lines, so they can share the vertical scroll model
        right.scrollPane().getVerticalScrollBar().setModel(left.scrollPane().getVerticalScrollBar().getModel());
        left.scrollPane().getVerticalScrollBar().getModel().addChangeListener(e -> fireNavigation());
        showMessage("");
    }

    void showMessage(String text) {
        shownPath = null;
        changes = List.of();
        current = -1;
        rows = List.of();
        matches = List.of();
        currentMatch = -1;
        overview.setFragments(List.of(), 0);
        message.setText(text);
        cards.show(this, MESSAGE);
        fireNavigation();
    }

    void showDiff(FileDiff diff) {
        if (diff.isBinary()) {
            showMessage(Messages.get("diff.placeholder.binary"));
            return;
        }
        if (diff.isTooLarge()) {
            showMessage(Messages.format("diff.placeholder.tooLarge",
                    String.valueOf(DiffReader.MAX_BYTES / (1024 * 1024))));
            return;
        }
        boolean sameFile = diff.path().equals(shownPath);
        int scrollValue = left.scrollPane().getVerticalScrollBar().getValue();

        String style = syntaxStyle(diff.path());
        left.setCaption(caption(diff.left()));
        right.setCaption(caption(diff.right()));
        List<InlineDiff.Result> changedWords = changedWords(diff.rows());
        left.setRows(diff.rows(), changedWords, style);
        right.setRows(diff.rows(), changedWords, style);
        overview.setFragments(DiffChanges.fragments(diff.rows()), diff.rows().size());
        shownPath = diff.path();
        changes = DiffChanges.starts(diff.rows());
        if (!sameFile || current >= changes.size()) {
            current = -1;
        }
        rows = diff.rows();
        matches = DiffSearch.find(rows, query, matchCase);
        if (!sameFile || currentMatch >= matches.size()) {
            currentMatch = -1;
        }
        showMatches();
        cards.show(this, DIFF);
        if (sameFile) {
            SwingUtilities.invokeLater(() -> left.scrollPane().getVerticalScrollBar().setValue(scrollValue));
        } else if (!changes.isEmpty()) {
            // a newly selected file opens at its first change; scroll again once the new text is laid out
            goTo(0);
            String path = shownPath;
            SwingUtilities.invokeLater(() -> {
                if (path.equals(shownPath) && current == 0) {
                    goTo(0);
                }
            });
        }
        fireNavigation();
    }

    /** Called when the shown diff, the scroll position or the navigated fragment changes. */
    void addNavigationListener(Runnable listener) {
        navigationListeners.add(listener);
    }

    boolean hasNextChange() {
        return DiffChanges.next(changes, reference(true)) >= 0;
    }

    boolean hasPreviousChange() {
        return DiffChanges.previous(changes, reference(false)) >= 0;
    }

    void nextChange() {
        goTo(DiffChanges.next(changes, reference(true)));
    }

    void previousChange() {
        goTo(DiffChanges.previous(changes, reference(false)));
    }

    /**
     * Searches both sides for {@code text} (plain text), highlights the matches and scrolls to the first one
     * at or below the top of the view; an empty text ends the search.
     */
    void search(String text, boolean caseSensitive) {
        query = text == null ? "" : text;
        matchCase = caseSensitive;
        matches = DiffSearch.find(rows, query, matchCase);
        currentMatch = DiffSearch.firstFrom(matches, topLine());
        showMatches();
        scrollToMatch();
        fireNavigation();
    }

    /** Goes to the next match, after the last one back to the first. */
    void nextMatch() {
        if (matches.isEmpty()) {
            return;
        }
        currentMatch = currentMatch < 0 ? DiffSearch.firstFrom(matches, topLine())
                : (currentMatch + 1) % matches.size();
        showMatches();
        scrollToMatch();
        fireNavigation();
    }

    /** Goes to the previous match, before the first one to the last. */
    void previousMatch() {
        if (matches.isEmpty()) {
            return;
        }
        int from = currentMatch < 0 ? DiffSearch.firstFrom(matches, topLine()) : currentMatch;
        currentMatch = (from - 1 + matches.size()) % matches.size();
        showMatches();
        scrollToMatch();
        fireNavigation();
    }

    /** "2 of 5" at a match, "5 match(es)", "No matches"; empty without a search. */
    String matchesText() {
        if (query.isEmpty()) {
            return "";
        }
        if (matches.isEmpty()) {
            return Messages.get("diff.search.none");
        }
        String total = String.valueOf(matches.size());
        return currentMatch < 0 ? Messages.format("diff.search.count", total)
                : Messages.format("diff.changes.position", String.valueOf(currentMatch + 1), total);
    }

    /** The match gone to last, or null (tests). */
    DiffSearch.Match currentMatch() {
        return currentMatch < 0 ? null : matches.get(currentMatch);
    }

    private void showMatches() {
        DiffSearch.Match match = currentMatch();
        left.showMatches(matches, match);
        right.showMatches(matches, match);
    }

    private void scrollToMatch() {
        DiffSearch.Match match = currentMatch();
        if (match != null) {
            (match.left() ? left : right).scrollToMatch(match, CONTEXT_LINES);
        }
    }

    /** "3 of 7" after navigating, "7 change(s)" otherwise; empty without a diff or changes. */
    String changesText() {
        if (changes.isEmpty()) {
            return "";
        }
        String total = String.valueOf(changes.size());
        return navigated() ? Messages.format("diff.changes.position", String.valueOf(current + 1), total)
                : Messages.format("diff.changes", total);
    }

    /** First row of the fragment navigated to last, -1 for none (tests). */
    int currentChangeRow() {
        return current < 0 ? -1 : changes.get(current);
    }

    private boolean navigated() {
        return current >= 0 && topLine() == navigatedTop;
    }

    /**
     * Row that "next/previous" counts from: the fragment navigated to last, or, after the user scrolled,
     * the rows at the top of the view.
     */
    private int reference(boolean forNext) {
        if (navigated()) {
            return changes.get(current);
        }
        int top = topLine();
        if (top == 0) {
            return forNext ? -1 : 0;
        }
        return forNext ? top + CONTEXT_LINES - 1 : top + CONTEXT_LINES;
    }

    private void goTo(int index) {
        if (index < 0) {
            return;
        }
        current = index;
        JScrollBar bar = left.scrollPane().getVerticalScrollBar();
        int lineHeight = Math.max(1, left.textArea().getLineHeight());
        bar.setValue(Math.max(0, changes.get(index) - CONTEXT_LINES) * lineHeight);
        navigatedTop = topLine();
        fireNavigation();
    }

    private int topLine() {
        int lineHeight = Math.max(1, left.textArea().getLineHeight());
        return left.scrollPane().getVerticalScrollBar().getValue() / lineHeight;
    }

    private void fireNavigation() {
        for (Runnable listener : navigationListeners) {
            listener.run();
        }
    }

    /** Per row, the changed words of a changed row, null for other rows. */
    static List<InlineDiff.Result> changedWords(List<DiffRow> rows) {
        List<InlineDiff.Result> words = new ArrayList<>(rows.size());
        for (DiffRow row : rows) {
            words.add(row.type() == DiffRow.Type.CHANGED ? InlineDiff.compute(row.leftText(), row.rightText()) : null);
        }
        return words;
    }

    /** Scrolls so that {@code row} is near the top, below a few lines of context (overview clicks). */
    void scrollToRow(int row) {
        JScrollBar bar = left.scrollPane().getVerticalScrollBar();
        int lineHeight = Math.max(1, left.textArea().getLineHeight());
        bar.setValue(Math.max(0, row - CONTEXT_LINES) * lineHeight);
    }

    DiffOverview overview() {
        return overview;
    }

    /** Path of the file currently displayed, or null when a message is shown. */
    String shownPath() {
        return shownPath;
    }

    DiffSide left() {
        return left;
    }

    DiffSide right() {
        return right;
    }

    static String caption(FileVersion version) {
        String source;
        switch (version.source()) {
            case HEAD:
                source = Messages.get("diff.source.head");
                break;
            case INDEX:
                source = Messages.get("diff.source.index");
                break;
            case WORKING_TREE:
            default:
                source = Messages.get("diff.source.workingTree");
                break;
        }
        return version.exists() ? source : Messages.format("diff.source.missing", source);
    }

    /** Syntax highlighting by file name (extension); plain text if unknown. */
    static String syntaxStyle(String path) {
        String name = path.substring(path.lastIndexOf('/') + 1);
        String style = FileTypeUtil.get().guessContentType(new File(name), true);
        return style != null ? style : SyntaxConstants.SYNTAX_STYLE_NONE;
    }
}
