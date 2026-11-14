package cz.wingit.ui.common;

import java.awt.Graphics;

import javax.swing.JList;

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
        if (getModel().getSize() == 0) {
            Placeholder.paint(g, this, placeholder);
        }
    }
}
