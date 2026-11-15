package cz.wingit.ui.main;

import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.util.Map;

import javax.swing.Action;
import javax.swing.JButton;
import javax.swing.JToolBar;
import javax.swing.KeyStroke;
import javax.swing.SwingConstants;

import cz.wingit.ui.Messages;
import cz.wingit.ui.common.Icons;

/**
 * Main toolbar: icon above label. Buttons without an action are placeholders (disabled) until
 * their Git operation is implemented.
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

    /** @param actions button id to action; the button follows the action's enabled state */
    public MainToolBar(Map<String, Action> actions) {
        setFloatable(false);
        for (int i = 0; i < GROUPS.length; i++) {
            if (i > 0) {
                addSeparator();
            }
            for (String id : GROUPS[i]) {
                add(createButton(id, actions.get(id)));
            }
        }
    }

    private static JButton createButton(String id, Action action) {
        JButton button = new JButton();
        if (action != null) {
            button.setAction(action);
            button.setToolTipText(action.getValue(Action.NAME) + acceleratorHint(action));
        } else {
            button.setEnabled(false);
        }
        // Short toolbar label and icon instead of the menu text ("Commit...").
        button.setText(Messages.get("toolbar." + id));
        button.setIcon(Icons.toolbar(id));
        button.setVerticalTextPosition(SwingConstants.BOTTOM);
        button.setHorizontalTextPosition(SwingConstants.CENTER);
        button.setFocusable(false);
        return button;
    }

    private static String acceleratorHint(Action action) {
        Object key = action.getValue(Action.ACCELERATOR_KEY);
        if (!(key instanceof KeyStroke stroke)) {
            return "";
        }
        String modifiers = InputEvent.getModifiersExText(stroke.getModifiers());
        String keyText = KeyEvent.getKeyText(stroke.getKeyCode());
        return " (" + (modifiers.isEmpty() ? keyText : modifiers + "+" + keyText) + ")";
    }
}
