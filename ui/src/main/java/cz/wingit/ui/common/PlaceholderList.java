package cz.wingit.ui.common;

import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.RenderingHints;

import javax.swing.JList;
import javax.swing.UIManager;

/**
 * JList that paints a dimmed hint while it has no items.
 */
public class PlaceholderList<E> extends JList<E> {

    private static final long serialVersionUID = 1L;

    private final String placeholder;

    public PlaceholderList(String placeholder) {
        this.placeholder = placeholder;
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        if (getModel().getSize() > 0 || placeholder == null || placeholder.isEmpty()) {
            return;
        }
        Graphics2D g2 = (Graphics2D) g.create();
        try {
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g2.setColor(UIManager.getColor("Label.disabledForeground"));
            g2.setFont(getFont());
            Insets insets = getInsets();
            g2.drawString(placeholder, insets.left + 8, insets.top + 8 + g2.getFontMetrics().getAscent());
        } finally {
            g2.dispose();
        }
    }
}
