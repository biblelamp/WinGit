package cz.wingit.ui.common;

import com.formdev.flatlaf.extras.FlatSVGIcon;

/**
 * SVG icons bundled in {@code cz/wingit/ui/icons}. Icons use the IntelliJ "Actions.*" colors
 * (e.g. #6E6E6E, #389FD6), which FlatSVGIcon maps to the current theme; disabled variants
 * are derived automatically.
 */
public final class Icons {

    public static final int TOOLBAR_SIZE = 20;

    private static final String BASE = "cz/wingit/ui/icons/";

    private Icons() {
    }

    /** Toolbar icon {@code icons/toolbar/<name>.svg}. */
    public static FlatSVGIcon toolbar(String name) {
        return new FlatSVGIcon(BASE + "toolbar/" + name + ".svg", TOOLBAR_SIZE, TOOLBAR_SIZE);
    }
}
