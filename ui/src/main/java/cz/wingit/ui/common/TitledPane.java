package cz.wingit.ui.common;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Insets;

import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;

/**
 * A pane with a compact header line (title on the left, optional tools on the right)
 * above its content. Base container for all main-window panes.
 */
public class TitledPane extends JPanel {

    private static final long serialVersionUID = 1L;

    private final JPanel header = new JPanel(new BorderLayout()) {
        private static final long serialVersionUID = 1L;

        /**
         * At least as tall as a header holding a text field (the Files filter), so panes side by side have
         * their titles and separator lines at the same height whether they have header tools or not.
         */
        @Override
        public Dimension getPreferredSize() {
            Dimension size = super.getPreferredSize();
            Insets insets = getInsets();
            int withField = new JTextField().getPreferredSize().height + insets.top + insets.bottom;
            return new Dimension(size.width, Math.max(size.height, withField));
        }
    };
    private JComponent headerMain;

    public TitledPane(String title, JComponent content) {
        super(new BorderLayout());

        headerMain = new JLabel(title);
        header.add(headerMain, BorderLayout.WEST);
        header.setBorder(SeparatorBorder.bottom(3, 6));

        add(header, BorderLayout.NORTH);
        add(content, BorderLayout.CENTER);
    }

    /** Changes the title text, unless the title was replaced by {@link #setHeaderMain}. */
    protected void setTitle(String title) {
        if (headerMain instanceof JLabel label) {
            label.setText(title);
        }
    }

    /** Replaces the title on the left side of the header with another component (e.g. a search field). */
    protected void setHeaderMain(JComponent main) {
        header.remove(headerMain);
        headerMain = main;
        header.add(main, BorderLayout.WEST);
    }

    /** Places a component (e.g. a filter field or small toolbar) on the right side of the header. */
    protected void setHeaderTools(JComponent tools) {
        header.add(tools, BorderLayout.EAST);
    }
}
