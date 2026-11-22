package cz.wingit.ui.settings;

import java.util.Objects;

import cz.wingit.ui.Language;
import cz.wingit.ui.Theme;

/**
 * Language and theme of the user interface, as chosen in the Settings dialog. Both apply at the next start.
 */
public record Appearance(Language language, Theme.Mode theme) {

    public static final Appearance DEFAULT = new Appearance(Language.SYSTEM, Theme.Mode.LIGHT);

    public Appearance {
        Objects.requireNonNull(language, "language");
        Objects.requireNonNull(theme, "theme");
    }
}
