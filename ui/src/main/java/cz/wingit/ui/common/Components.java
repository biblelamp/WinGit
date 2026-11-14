package cz.wingit.ui.common;

import java.awt.Component;

import javax.swing.BorderFactory;
import javax.swing.JScrollPane;

/**
 * Small factory helpers shared by panes.
 */
public final class Components {

    private Components() {
    }

    /** Scroll pane without its own border; panes draw separators themselves. */
    public static JScrollPane borderlessScroll(Component view) {
        JScrollPane scroll = new JScrollPane(view);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        return scroll;
    }
}
