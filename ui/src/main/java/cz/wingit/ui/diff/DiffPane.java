package cz.wingit.ui.diff;

import java.awt.BorderLayout;

import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSplitPane;

import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;
import org.fife.ui.rtextarea.RTextScrollPane;

import cz.wingit.ui.Messages;
import cz.wingit.ui.common.TitledPane;

/**
 * Side-by-side diff of the selected file (base on the left, working copy on the right).
 */
public final class DiffPane extends TitledPane {

    private static final long serialVersionUID = 1L;

    public DiffPane() {
        super(Messages.get("diff.title"), createSplit());
    }

    private static JSplitPane createSplit() {
        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,
                side(Messages.get("diff.left")),
                side(Messages.get("diff.right")));
        split.setResizeWeight(0.5);
        split.setBorder(BorderFactory.createEmptyBorder());
        return split;
    }

    private static JPanel side(String caption) {
        RSyntaxTextArea area = new RSyntaxTextArea();
        area.setEditable(false);
        area.setHighlightCurrentLine(false);
        area.setCodeFoldingEnabled(false);

        RTextScrollPane scroll = new RTextScrollPane(area);
        scroll.setBorder(BorderFactory.createEmptyBorder());

        JLabel label = new JLabel(caption);
        label.putClientProperty("FlatLaf.styleClass", "small");
        label.setBorder(BorderFactory.createEmptyBorder(2, 6, 2, 6));

        JPanel panel = new JPanel(new BorderLayout());
        panel.add(label, BorderLayout.NORTH);
        panel.add(scroll, BorderLayout.CENTER);
        return panel;
    }
}
