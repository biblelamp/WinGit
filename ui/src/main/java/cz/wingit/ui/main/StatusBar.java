package cz.wingit.ui.main;

import java.awt.BorderLayout;

import javax.swing.JLabel;
import javax.swing.JPanel;

import cz.wingit.ui.Messages;
import cz.wingit.ui.common.SeparatorBorder;

/**
 * Single-line status bar at the bottom of the main window.
 */
public final class StatusBar extends JPanel {

    private static final long serialVersionUID = 1L;

    private final JLabel message = new JLabel(Messages.get("status.ready"));

    public StatusBar() {
        super(new BorderLayout());
        setBorder(SeparatorBorder.top(2, 6));
        add(message, BorderLayout.WEST);
    }

    public void setMessage(String text) {
        message.setText(text);
    }
}
