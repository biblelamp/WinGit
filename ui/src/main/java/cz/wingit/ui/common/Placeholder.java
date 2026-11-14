package cz.wingit.ui.common;

import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.RenderingHints;

import javax.swing.JComponent;
import javax.swing.UIManager;

import com.formdev.flatlaf.util.UIScale;

/**
 * Paints a dimmed hint into the top-left corner of an empty list or table.
 */
final class Placeholder {

    private Placeholder() {
    }

    static void paint(Graphics g, JComponent component, String text) {
        if (text == null || text.isEmpty()) {
            return;
        }
        Graphics2D g2 = (Graphics2D) g.create();
        try {
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g2.setColor(UIManager.getColor("Label.disabledForeground"));
            g2.setFont(component.getFont());
            Insets insets = component.getInsets();
            int pad = UIScale.scale(8);
            g2.drawString(text, insets.left + pad, insets.top + pad + g2.getFontMetrics().getAscent());
        } finally {
            g2.dispose();
        }
    }
}
