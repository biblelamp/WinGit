package cz.wingit.ui;

import java.awt.Color;

import javax.swing.UIManager;

import com.formdev.flatlaf.FlatDarkLaf;
import com.formdev.flatlaf.FlatLightLaf;

/**
 * Installs the FlatLaf look and feel. Lists, tables and trees get a light selection background on which
 * rows keep their normal text colors (dimmed branches, ref labels stay readable); while the component
 * has no focus the selection is gray.
 */
public final class Theme {

    public enum Mode { LIGHT, DARK }

    /** Selection background of lists, tables and trees. */
    public static final Color SELECTION_LIGHT = new Color(0xCCE8FF);
    public static final Color SELECTION_DARK = new Color(0x2F4B6B);
    /** Selection background while the component has no focus. */
    public static final Color SELECTION_INACTIVE_LIGHT = new Color(0xDADADA);
    public static final Color SELECTION_INACTIVE_DARK = new Color(0x45484C);

    private Theme() {
    }

    /** Must be called before any Swing component is created. */
    public static void install(Mode mode) {
        if (mode == Mode.DARK) {
            FlatDarkLaf.setup();
        } else {
            FlatLightLaf.setup();
        }
        boolean dark = mode == Mode.DARK;
        Color selection = dark ? SELECTION_DARK : SELECTION_LIGHT;
        Color inactive = dark ? SELECTION_INACTIVE_DARK : SELECTION_INACTIVE_LIGHT;
        for (String component : new String[] {"List", "Table", "Tree"}) {
            Color text = UIManager.getColor(component + ".foreground");
            UIManager.put(component + ".selectionBackground", selection);
            UIManager.put(component + ".selectionInactiveBackground", inactive);
            UIManager.put(component + ".selectionForeground", text);
            UIManager.put(component + ".selectionInactiveForeground", text);
        }
    }
}
