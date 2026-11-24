package cz.wingit.ui.settings;

import java.util.Objects;

/**
 * Everything the Settings dialog edits: the appearance (applied at the next start) and the automatic
 * fetch interval (applied at once).
 */
public record Preferences(Appearance appearance, FetchInterval fetch) {

    public static final Preferences DEFAULT = new Preferences(Appearance.DEFAULT, FetchInterval.DEFAULT);

    public Preferences {
        Objects.requireNonNull(appearance, "appearance");
        Objects.requireNonNull(fetch, "fetch");
    }
}
