package cz.wingit.ui.main;

import javax.swing.JButton;
import javax.swing.JToolBar;
import javax.swing.SwingConstants;

import cz.wingit.ui.Messages;
import cz.wingit.ui.common.Icons;

/**
 * Main toolbar: icon above label. Actions are placeholders (disabled) until Git operations are wired.
 */
public final class MainToolBar extends JToolBar {

    private static final long serialVersionUID = 1L;

    /** Button ids; each maps to {@code toolbar.<id>} text and {@code icons/toolbar/<id>.svg}. */
    static final String[][] GROUPS = {
            {"fetch", "pull", "push"},
            {"commit", "merge"},
            {"stage", "unstage", "discard"},
            {"stash", "history"},
    };

    public MainToolBar() {
        setFloatable(false);
        for (int i = 0; i < GROUPS.length; i++) {
            if (i > 0) {
                addSeparator();
            }
            for (String id : GROUPS[i]) {
                add(createButton(id));
            }
        }
    }

    private static JButton createButton(String id) {
        JButton button = new JButton(Messages.get("toolbar." + id), Icons.toolbar(id));
        button.setVerticalTextPosition(SwingConstants.BOTTOM);
        button.setHorizontalTextPosition(SwingConstants.CENTER);
        button.setEnabled(false);
        button.setFocusable(false);
        return button;
    }
}
