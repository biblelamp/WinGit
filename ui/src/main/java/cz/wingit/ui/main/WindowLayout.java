package cz.wingit.ui.main;

import java.util.Map;

/**
 * Saved geometry of the main window: its normal (not maximized) bounds, whether it is maximized,
 * and the dividers of the split panes, keyed by split name. All values are in screen pixels.
 */
public record WindowLayout(int x, int y, int width, int height, boolean maximized, Map<String, Divider> dividers) {

    public WindowLayout {
        dividers = Map.copyOf(dividers);
    }

    /**
     * A divider position together with the size of its split pane along the split axis at that time
     * ({@code 0} = unknown), so it can be restored correctly when the split pane gets a different size.
     */
    public record Divider(int location, int splitSize) {
    }
}
