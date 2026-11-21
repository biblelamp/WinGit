package cz.wingit.ui.diff;

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

    DiffView() {
        setLayout(cards);

        JPanel messagePanel = new JPanel(new GridBagLayout());
        message.setForeground(UIManager.getColor("Label.disabledForeground"));
        messagePanel.add(message);
        add(messagePanel, MESSAGE);

        add(new HalfSplitPane(left, right), DIFF);

        // aligned sides have the same number of lines, so they can share the vertical scroll model
        right.scrollPane().getVerticalScrollBar().setModel(left.scrollPane().getVerticalScrollBar().getModel());
        left.scrollPane().getVerticalScrollBar().getModel().addChangeListener(e -> fireNavigation());
        showMessage("");
    }

    void showMessage(String text) {
        shownPath = null;
        changes = List.of();
        current = -1;
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
        left.setRows(diff.rows(), style);
        right.setRows(diff.rows(), style);
        shownPath = diff.path();
        changes = DiffChanges.starts(diff.rows());
        if (!sameFile || current >= changes.size()) {
            current = -1;
        }
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
