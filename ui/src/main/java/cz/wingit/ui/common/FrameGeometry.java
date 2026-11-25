package cz.wingit.ui.common;

import java.awt.Dimension;
import java.awt.Frame;
import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;
import java.awt.Rectangle;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.swing.JFrame;

/**
 * Size and position of a window for saving and restoring ({@link WindowLayout}): remembers its bounds in the
 * normal (not maximized) state, which is what gets saved, and restores them only if they are still on a
 * screen. Use on the EDT only.
 */
public final class FrameGeometry {

    /** How much of the saved window's title strip must be on some screen to restore its position. */
    private static final int MIN_VISIBLE_WIDTH = 100;
    private static final int MIN_VISIBLE_HEIGHT = 20;

    private final JFrame frame;
    /** Bounds in the normal (not maximized) state; what we save and restore. */
    private Rectangle normalBounds;

    /** Starts tracking {@code frame}; call once it has its initial size and position. */
    public FrameGeometry(JFrame frame) {
        this.frame = frame;
        this.normalBounds = frame.getBounds();
        frame.addComponentListener(new ComponentAdapter() {
            @Override
            public void componentMoved(ComponentEvent e) {
                rememberNormalBounds();
            }

            @Override
            public void componentResized(ComponentEvent e) {
                rememberNormalBounds();
            }
        });
    }

    /** Current geometry with {@code dividers}, to be saved and passed to {@link #apply} next time. */
    public WindowLayout layout(Map<String, WindowLayout.Divider> dividers) {
        return new WindowLayout(normalBounds.x, normalBounds.y, normalBounds.width, normalBounds.height,
                isMaximized(), dividers);
    }

    /**
     * Restores the bounds and the maximized state of {@code layout} (not its dividers). Call before the window
     * is shown. A position that is no longer on any screen (e.g. a disconnected monitor) is ignored and the
     * window keeps its current bounds.
     */
    public void apply(WindowLayout layout) {
        Rectangle bounds = new Rectangle(layout.x(), layout.y(), layout.width(), layout.height());
        if (isOnScreen(bounds, screenBounds())) {
            Dimension min = frame.getMinimumSize();
            bounds.width = Math.max(bounds.width, min.width);
            bounds.height = Math.max(bounds.height, min.height);
            frame.setBounds(bounds);
            normalBounds = bounds;
        }
        if (layout.maximized()) {
            frame.setExtendedState(frame.getExtendedState() | Frame.MAXIMIZED_BOTH);
        }
    }

    /** True if enough of the window's title strip lies on one of the screens to grab it with the mouse. */
    public static boolean isOnScreen(Rectangle bounds, List<Rectangle> screens) {
        if (bounds.width <= 0 || bounds.height <= 0) {
            return false;
        }
        Rectangle titleStrip = new Rectangle(bounds.x, bounds.y, bounds.width, Math.min(bounds.height, 30));
        for (Rectangle screen : screens) {
            Rectangle visible = screen.intersection(titleStrip);
            if (visible.width >= MIN_VISIBLE_WIDTH && visible.height >= MIN_VISIBLE_HEIGHT) {
                return true;
            }
        }
        return false;
    }

    /** Preferred size, shrunk to 90 % of the usable screen area (taskbar excluded) if needed. */
    public static Dimension fitToScreen(int width, int height) {
        Rectangle usable = GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds();
        return new Dimension(
                Math.min(width, (int) (usable.width * 0.9)),
                Math.min(height, (int) (usable.height * 0.9)));
    }

    private static List<Rectangle> screenBounds() {
        List<Rectangle> screens = new ArrayList<>();
        for (GraphicsDevice device : GraphicsEnvironment.getLocalGraphicsEnvironment().getScreenDevices()) {
            screens.add(device.getDefaultConfiguration().getBounds());
        }
        return screens;
    }

    private boolean isMaximized() {
        return (frame.getExtendedState() & Frame.MAXIMIZED_BOTH) == Frame.MAXIMIZED_BOTH;
    }

    private void rememberNormalBounds() {
        if ((frame.getExtendedState() & (Frame.MAXIMIZED_BOTH | Frame.ICONIFIED)) == 0) {
            normalBounds = frame.getBounds();
        }
    }
}
