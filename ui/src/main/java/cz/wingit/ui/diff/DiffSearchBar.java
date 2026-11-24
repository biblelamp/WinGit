package cz.wingit.ui.diff;

import java.awt.FlowLayout;
import java.awt.event.ActionEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;

import javax.swing.AbstractAction;
import javax.swing.AbstractButton;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.JToggleButton;
import javax.swing.KeyStroke;
import javax.swing.UIManager;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

import com.formdev.flatlaf.FlatClientProperties;
import com.formdev.flatlaf.icons.FlatClearIcon;
import com.formdev.flatlaf.icons.FlatSearchIcon;

import cz.wingit.ui.Messages;
import cz.wingit.ui.common.Icons;
import cz.wingit.ui.common.SeparatorBorder;

/**
 * Search line of the diff: searches as you type, Enter / Shift+Enter go to the next / previous match.
 * Above the diff it is hidden until {@link #open()} (Ctrl+F) and Esc closes it, ending the search.
 * Permanent (in the header of a {@link DiffWindow}) it is always shown: Esc clears the search text,
 * and with no text leaves the key to the window (which closes).
 */
final class DiffSearchBar extends JPanel {

    private static final long serialVersionUID = 1L;

    private final DiffView view;
    private final JTextField field = new JTextField(24);
    private final JToggleButton matchCase = new JToggleButton("Aa");
    private final JLabel count = new JLabel();
    private final boolean permanent;

    DiffSearchBar(DiffView view, boolean permanent) {
        super(new FlowLayout(FlowLayout.LEFT, 4, permanent ? 0 : 2));
        this.view = view;
        this.permanent = permanent;
        if (!permanent) {
            setBorder(SeparatorBorder.bottom(0, 2));
        }

        field.putClientProperty(FlatClientProperties.PLACEHOLDER_TEXT, Messages.get("diff.search.placeholder"));
        field.putClientProperty(FlatClientProperties.TEXT_FIELD_LEADING_ICON, new FlatSearchIcon());
        field.putClientProperty(FlatClientProperties.TEXT_FIELD_SHOW_CLEAR_BUTTON, true);
        field.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                search();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                search();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                search();
            }
        });
        bind(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), "nextMatch", view::nextMatch);
        bind(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, InputEvent.SHIFT_DOWN_MASK), "previousMatch",
                view::previousMatch);
        field.getInputMap(JComponent.WHEN_FOCUSED).put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "escape");
        field.getActionMap().put("escape", new AbstractAction() {
            private static final long serialVersionUID = 1L;

            /** Disabled, the key is not used here and goes on to the window (DiffWindow closes). */
            @Override
            public boolean isEnabled() {
                return !permanent || !field.getText().isEmpty();
            }

            @Override
            public void actionPerformed(ActionEvent e) {
                if (permanent) {
                    field.setText("");
                } else {
                    close();
                }
            }
        });

        count.setForeground(UIManager.getColor("Label.disabledForeground"));
        matchCase.setToolTipText(Messages.get("diff.search.matchCase.tooltip"));
        matchCase.addActionListener(e -> search());

        add(field);
        add(count);
        add(toolButton(new JButton(), Icons.diff("previous-change"), "diff.search.previous.tooltip",
                view::previousMatch));
        add(toolButton(new JButton(), Icons.diff("next-change"), "diff.search.next.tooltip", view::nextMatch));
        add(toolButton(matchCase, null, "diff.search.matchCase.tooltip", null));
        if (!permanent) {
            add(toolButton(new JButton(), new FlatClearIcon(), "diff.search.close.tooltip", this::close));
        }

        view.addNavigationListener(() -> count.setText(view.matchesText()));
        setVisible(permanent);
    }

    /** Shows the search line and puts the cursor into it, keeping the last search text selected. */
    void open() {
        boolean wasVisible = isVisible();
        setVisible(true);
        field.selectAll();
        field.requestFocusInWindow();
        if (!wasVisible) {
            search();
        }
    }

    /** Hides the search line and removes the match highlights. */
    void close() {
        if (permanent || !isVisible()) {
            return;
        }
        setVisible(false);
        view.search("", false);
        view.left().textArea().requestFocusInWindow();
    }

    private void search() {
        if (isVisible()) {
            view.search(field.getText(), matchCase.isSelected());
        }
    }

    private void bind(KeyStroke key, String name, Runnable action) {
        field.getInputMap(JComponent.WHEN_FOCUSED).put(key, name);
        field.getActionMap().put(name, new AbstractAction() {
            private static final long serialVersionUID = 1L;

            @Override
            public void actionPerformed(ActionEvent e) {
                action.run();
            }
        });
    }

    private static AbstractButton toolButton(AbstractButton button, Icon icon, String tooltipKey, Runnable action) {
        if (icon != null) {
            button.setIcon(icon);
        }
        button.setToolTipText(Messages.get(tooltipKey));
        button.putClientProperty(FlatClientProperties.BUTTON_TYPE, FlatClientProperties.BUTTON_TYPE_TOOLBAR_BUTTON);
        button.setFocusable(false);
        if (action != null) {
            button.addActionListener(e -> action.run());
        }
        return button;
    }

    /** For tests. */
    JTextField field() {
        return field;
    }

    /** For tests. */
    JToggleButton matchCaseButton() {
        return matchCase;
    }

    /** For tests. */
    String countText() {
        return count.getText();
    }
}
