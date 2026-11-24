package cz.wingit.ui.diff;

import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.List;
import java.util.function.IntConsumer;

import javax.swing.JComponent;
import javax.swing.SwingUtilities;

import com.formdev.flatlaf.util.UIScale;

import cz.wingit.ui.Messages;

/**
 * Narrow strip beside the diff with a mark for every changed fragment at its place in the whole file:
 * red for removed lines, green for added ones, half red / half green for changed ones. A click scrolls
 * the diff to that place.
 */
final class DiffOverview extends JComponent {

    private static final long serialVersionUID = 1L;

    static final Color REMOVED = new Color(0xE8, 0x5C, 0x64, 210);
    static final Color ADDED = new Color(0x5C, 0xC8, 0x5C, 210);
    private static final int WIDTH = 10;
    private static final int MIN_MARK_HEIGHT = 3;

    private transient List<DiffChanges.Fragment> fragments = List.of();
    private int rowCount;

    DiffOverview(IntConsumer scrollToRow) {
        setToolTipText(Messages.get("diff.overview.tooltip"));
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                if (SwingUtilities.isLeftMouseButton(e) && rowCount > 0) {
                    scrollToRow.accept(rowAt(e.getY(), getHeight(), rowCount));
                }
            }
        });
    }

    /** The fragments of a diff with {@code rowCount} rows; none to show an empty strip. */
    void setFragments(List<DiffChanges.Fragment> fragments, int rowCount) {
        this.fragments = fragments;
        this.rowCount = rowCount;
        repaint();
    }

    /** Diff row shown at height {@code y} of a strip {@code height} pixels high. */
    static int rowAt(int y, int height, int rowCount) {
        if (height <= 0) {
            return 0;
        }
        long row = (long) Math.max(0, y) * rowCount / height;
        return (int) Math.min(rowCount - 1L, row);
    }

    @Override
    public Dimension getPreferredSize() {
        return new Dimension(UIScale.scale(WIDTH), 0);
    }

    @Override
    protected void paintComponent(Graphics g) {
        if (rowCount <= 0) {
            return;
        }
        int width = getWidth();
        int height = getHeight();
        int inset = UIScale.scale(2);
        int markWidth = width - 2 * inset;
        int minHeight = UIScale.scale(MIN_MARK_HEIGHT);
        for (DiffChanges.Fragment fragment : fragments) {
            int top = (int) ((long) fragment.start() * height / rowCount);
            int bottom = (int) ((long) fragment.end() * height / rowCount);
            int markHeight = Math.max(minHeight, bottom - top);
            if (fragment.removes() && fragment.adds()) {
                int half = markWidth / 2;
                g.setColor(REMOVED);
                g.fillRect(inset, top, half, markHeight);
                g.setColor(ADDED);
                g.fillRect(inset + half, top, markWidth - half, markHeight);
            } else {
                g.setColor(fragment.removes() ? REMOVED : ADDED);
                g.fillRect(inset, top, markWidth, markHeight);
            }
        }
    }

    /** For tests. */
    List<DiffChanges.Fragment> fragments() {
        return fragments;
    }
}
