package cz.wingit.ui;

import java.text.MessageFormat;
import java.util.HashSet;
import java.util.Locale;
import java.util.ResourceBundle;
import java.util.Set;

import javax.swing.UIManager;

/**
 * Access to localized UI strings ({@code cz/wingit/ui/messages*.properties}). The language is chosen once at
 * startup with {@link #setLanguage} (before any component is created); without it the system language is used.
 */
public final class Messages {

    private static final String BASE_NAME = "cz.wingit.ui.messages";
    /** Keys with this prefix are Swing's own texts (dialog buttons, file chooser) put into {@link UIManager}. */
    private static final String SWING_PREFIX = "swing.";

    private static volatile ResourceBundle bundle;
    private static volatile Language language;
    /** Swing keys put into {@link UIManager} by the last {@link #setLanguage}. */
    private static final Set<String> installedSwingKeys = new HashSet<>();

    private Messages() {
    }

    /**
     * Selects the UI language ({@link Language#SYSTEM} = the operating system's, if translated) and installs
     * the translated Swing texts. Call before any Swing component is created.
     */
    public static void setLanguage(Language requested) {
        Language resolved = requested.resolve(Locale.getDefault());
        ResourceBundle loaded = load(resolved);
        language = resolved;
        bundle = loaded;
        installSwingTexts(loaded);
    }

    /** The language in use (never {@link Language#SYSTEM}). */
    public static Language language() {
        bundle();
        return language;
    }

    public static String get(String key) {
        return bundle().getString(key);
    }

    public static String format(String key, Object... args) {
        return MessageFormat.format(get(key), args);
    }

    /** A string in {@code language} (not the one in use), e.g. to ask about switching to it. */
    public static String get(Language language, String key) {
        return load(language.resolve(Locale.getDefault())).getString(key);
    }

    private static ResourceBundle bundle() {
        ResourceBundle current = bundle;
        if (current == null) {
            Language resolved = Language.SYSTEM.resolve(Locale.getDefault());
            current = load(resolved);
            language = resolved;
            bundle = current;
        }
        return current;
    }

    /** No fallback to the default locale: English chosen on a Czech system must stay English. */
    private static ResourceBundle load(Language language) {
        return ResourceBundle.getBundle(BASE_NAME, language.locale(),
                ResourceBundle.Control.getNoFallbackControl(ResourceBundle.Control.FORMAT_PROPERTIES));
    }

    /**
     * The JDK has no Russian or Czech texts for option panes and file choosers; our translations override
     * them. English keeps the JDK's own texts (the base bundle has no such keys).
     */
    private static synchronized void installSwingTexts(ResourceBundle loaded) {
        installedSwingKeys.forEach(key -> UIManager.put(key, null)); // back to the look and feel's own texts
        installedSwingKeys.clear();
        for (String key : loaded.keySet()) {
            if (key.startsWith(SWING_PREFIX)) {
                String uiKey = key.substring(SWING_PREFIX.length());
                String value = loaded.getString(key);
                // Mnemonics stay strings: the option pane casts them to String (a file chooser takes both),
                // an Integer there made every Yes/No question fail silently.
                UIManager.put(uiKey, uiKey.endsWith("Mnemonic") ? value.trim() : value);
                installedSwingKeys.add(uiKey);
            }
        }
    }
}
