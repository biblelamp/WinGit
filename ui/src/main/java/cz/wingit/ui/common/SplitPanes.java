package cz.wingit.ui.common;

import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JSplitPane;
import javax.swing.plaf.basic.BasicSplitPaneUI;

import com.formdev.flatlaf.util.UIScale;

/**
 * The split panes of one window by name, so their dividers can be saved and restored ({@link WindowLayout}).
 * Keep the names stable: they are the keys in saved layouts. Use on the EDT only.
 */
public final class SplitPanes {

    private final Map<String, JSplitPane> splits = new LinkedHashMap<>();
    /** Pending divider restores; all stop once the user grabs any divider. */
    private final List<Runnable> stopRestoring = new ArrayList<>();

    /**
     * A borderless, continuously laid out split pane named {@code name}, its divider at
     * {@code dividerLocation} (unscaled pixels) until a saved one is restored.
     */
    public JSplitPane create(String name, int orientation, JComponent first, JComponent second,
                             int dividerLocation, double resizeWeight) {
        JSplitPane split = new JSplitPane(orientation, first, second);
        split.setDividerLocation(UIScale.scale(dividerLocation));
        split.setResizeWeight(resizeWeight);
        split.setContinuousLayout(true);
        split.setBorder(BorderFactory.createEmptyBorder());
        if (split.getUI() instanceof BasicSplitPaneUI ui) {
            ui.getDivider().addMouseListener(new MouseAdapter() {
                @Override
                public void mousePressed(MouseEvent e) {
                    stopRestoring();
                }
            });
        }
        splits.put(name, split);
        return split;
    }

    /** Split pane by name, {@code null} if there is none. */
    public JSplitPane get(String name) {
        return splits.get(name);
    }

    /** Current dividers, keyed by split name. */
    public Map<String, WindowLayout.Divider> dividers() {
        Map<String, WindowLayout.Divider> dividers = new LinkedHashMap<>();
        splits.forEach((name, split) ->
                dividers.put(name, new WindowLayout.Divider(split.getDividerLocation(), length(split))));
        return dividers;
    }

    /**
     * Restores saved dividers; unknown names and negative locations are ignored. Call before the window
     * is shown: each divider is placed as if its split pane had been resized from the saved size to its
     * current one, so the window can get its final size (e.g. maximized) in several layout passes.
     */
    public void restore(Map<String, WindowLayout.Divider> saved) {
        stopRestoring();
        saved.forEach((name, divider) -> {
            JSplitPane split = splits.get(name);
            if (split != null && divider != null && divider.location() >= 0) {
                restore(split, divider);
            }
        });
    }

    /**
     * While the window gets its final size, nested split panes are resized several times and Swing's own
     * layout moves their dividers unpredictably (it keeps absolute locations before the first paint and
     * re-balances them afterwards). So re-place the divider from the saved values on every resize, until
     * the user grabs the divider; from then on Swing's normal behavior applies.
     */
    private void restore(JSplitPane split, WindowLayout.Divider divider) {
        ComponentAdapter onResize = new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                int length = length(split);
                if (length > 0) {
                    split.setDividerLocation(location(divider, length, split.getResizeWeight()));
                }
            }
        };
        split.addComponentListener(onResize);
        stopRestoring.add(() -> split.removeComponentListener(onResize));
        onResize.componentResized(null);
    }

    private void stopRestoring() {
        stopRestoring.forEach(Runnable::run);
        stopRestoring.clear();
    }

    /** Where a divider saved at {@code divider.splitSize()} ends up in a split pane of {@code length}. */
    public static int location(WindowLayout.Divider divider, int length, double resizeWeight) {
        if (divider.splitSize() <= 0) {
            return divider.location();
        }
        return Math.max(0, (int) Math.round(divider.location() + resizeWeight * (length - divider.splitSize())));
    }

    private static int length(JSplitPane split) {
        return split.getOrientation() == JSplitPane.HORIZONTAL_SPLIT ? split.getWidth() : split.getHeight();
    }
}
