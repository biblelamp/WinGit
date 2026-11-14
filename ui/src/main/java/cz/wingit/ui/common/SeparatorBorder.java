package cz.wingit.ui.common;

import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Insets;

import javax.swing.UIManager;
import javax.swing.border.AbstractBorder;

import com.formdev.flatlaf.util.UIScale;

/**
 * One-pixel separator line(s) plus padding. The color is resolved from the current
 * look and feel at paint time, so it follows runtime theme switches.
 */
public final class SeparatorBorder extends AbstractBorder {

    private static final long serialVersionUID = 1L;

    private final boolean top;
    private final boolean bottom;
    private final Insets padding;

    private SeparatorBorder(boolean top, boolean bottom, Insets padding) {
        this.top = top;
        this.bottom = bottom;
        this.padding = padding;
    }

    public static SeparatorBorder top(int vPad, int hPad) {
        return new SeparatorBorder(true, false, new Insets(vPad, hPad, vPad, hPad));
    }

    public static SeparatorBorder bottom(int vPad, int hPad) {
        return new SeparatorBorder(false, true, new Insets(vPad, hPad, vPad, hPad));
    }

    @Override
    public void paintBorder(Component c, Graphics g, int x, int y, int width, int height) {
        Color color = UIManager.getColor("Component.borderColor");
        if (color == null) {
            return;
        }
        g.setColor(color);
        if (top) {
            g.fillRect(x, y, width, 1);
        }
        if (bottom) {
            g.fillRect(x, y + height - 1, width, 1);
        }
    }

    @Override
    public Insets getBorderInsets(Component c, Insets insets) {
        insets.top = UIScale.scale(padding.top) + (top ? 1 : 0);
        insets.bottom = UIScale.scale(padding.bottom) + (bottom ? 1 : 0);
        insets.left = UIScale.scale(padding.left);
        insets.right = UIScale.scale(padding.right);
        return insets;
    }
}
