package cz.wingit.ui;

import com.formdev.flatlaf.FlatDarkLaf;
import com.formdev.flatlaf.FlatLightLaf;

/**
 * Installs the FlatLaf look and feel.
 */
public final class Theme {

    public enum Mode { LIGHT, DARK }

    private Theme() {
    }

    /** Must be called before any Swing component is created. */
    public static void install(Mode mode) {
        if (mode == Mode.DARK) {
            FlatDarkLaf.setup();
        } else {
            FlatLightLaf.setup();
        }
    }
}
