package cz.wingit.ui.common;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Font;

import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.UIManager;

/**
 * Banner at the top of a dialog: bold title and a one-line description (which can show an error).
 */
public final class DialogHeader extends JPanel {

    private static final long serialVersionUID = 1L;

    private final JLabel description = new JLabel();
    private final transient String defaultText;

    public DialogHeader(String title, String descriptionText) {
        super(new BorderLayout(0, 4));
        this.defaultText = descriptionText;
        JLabel titleLabel = new JLabel(title);
        titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD));
        setBackground(UIManager.getColor("TextField.background"));
        setBorder(BorderFactory.createCompoundBorder(SeparatorBorder.bottom(0, 0),
                BorderFactory.createEmptyBorder(10, 12, 10, 12)));
        add(titleLabel, BorderLayout.NORTH);
        add(description, BorderLayout.CENTER);
        showDescription();
    }

    /** Shows {@code message} in the error color instead of the description. */
    public void showError(String message) {
        description.setText(message);
        Color error = UIManager.getColor("Component.error.focusedBorderColor");
        description.setForeground(error != null ? error : Color.RED);
    }

    /** Back to the normal description. */
    public void showDescription() {
        description.setText(defaultText);
        description.setForeground(UIManager.getColor("Label.foreground"));
    }

    /** Currently shown description or error text. */
    public String text() {
        return description.getText();
    }
}
