package cz.wingit.ui.diff;

import java.awt.Component;
import java.awt.Insets;

import javax.swing.BorderFactory;
import javax.swing.JSplitPane;

/**
 * Left/right split that always divides its width in half, whatever the content: the two sides of a diff
 * are equally important. The divider cannot be dragged.
 */
final class HalfSplitPane extends JSplitPane {

    private static final long serialVersionUID = 1L;

    HalfSplitPane(Component left, Component right) {
        super(HORIZONTAL_SPLIT, left, right);
        setResizeWeight(0.5);
        setBorder(BorderFactory.createEmptyBorder());
        setEnabled(false); // no dragging: the position is fixed
    }

    @Override
    public void doLayout() {
        int half = half();
        // only when it differs: moving the divider revalidates, which would lay out again forever
        if (getWidth() > 0 && getDividerLocation() != half) {
            setDividerLocation(half);
        }
        super.doLayout();
    }

    /** Divider location that leaves both sides the same width (the left one a pixel narrower if odd). */
    int half() {
        Insets insets = getInsets();
        int available = getWidth() - insets.left - insets.right - getDividerSize();
        return insets.left + Math.max(0, available / 2);
    }
}
