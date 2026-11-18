package cz.wingit.ui.diff;

import java.awt.CardLayout;
import java.awt.GridBagLayout;
import java.io.File;

import javax.swing.JLabel;
import javax.swing.JPanel;
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
 * Both sides scroll vertically together; their lines are aligned by filler lines.
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

    DiffView() {
        setLayout(cards);

        JPanel messagePanel = new JPanel(new GridBagLayout());
        message.setForeground(UIManager.getColor("Label.disabledForeground"));
        messagePanel.add(message);
        add(messagePanel, MESSAGE);

        add(new HalfSplitPane(left, right), DIFF);

        // aligned sides have the same number of lines, so they can share the vertical scroll model
        right.scrollPane().getVerticalScrollBar().setModel(left.scrollPane().getVerticalScrollBar().getModel());
        showMessage("");
    }

    void showMessage(String text) {
        shownPath = null;
        message.setText(text);
        cards.show(this, MESSAGE);
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
        cards.show(this, DIFF);
        if (sameFile) {
            SwingUtilities.invokeLater(() -> left.scrollPane().getVerticalScrollBar().setValue(scrollValue));
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
