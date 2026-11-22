package cz.wingit.ui;

import java.util.Locale;

/**
 * Language of the user interface. {@link #SYSTEM} follows the operating system when we have a translation
 * for its language, otherwise English.
 */
public enum Language {
    SYSTEM(null, null),
    ENGLISH("en", "English"),
    RUSSIAN("ru", "Русский"),
    CZECH("cs", "Čeština");

    private final String code;
    private final String nativeName;

    Language(String code, String nativeName) {
        this.code = code;
        this.nativeName = nativeName;
    }

    /** ISO 639 code used in settings ({@code system} for {@link #SYSTEM}). */
    public String code() {
        return code == null ? "system" : code;
    }

    /** Name of the language in itself (not translated); {@code null} for {@link #SYSTEM}. */
    public String nativeName() {
        return nativeName;
    }

    /** The concrete language to use; never {@link #SYSTEM}. */
    public Language resolve(Locale system) {
        if (this != SYSTEM) {
            return this;
        }
        String systemLanguage = system.getLanguage();
        for (Language language : values()) {
            if (language.code != null && language.code.equals(systemLanguage)) {
                return language;
            }
        }
        return ENGLISH;
    }

    /** Locale of the UI strings; never called on {@link #SYSTEM} after {@link #resolve}. */
    public Locale locale() {
        return code == null ? Locale.ROOT : Locale.forLanguageTag(code);
    }

    /** Parses a {@link #code()}; unknown or missing values give {@link #SYSTEM}. */
    public static Language fromCode(String code) {
        if (code != null) {
            for (Language language : values()) {
                if (language.code().equalsIgnoreCase(code.trim())) {
                    return language;
                }
            }
        }
        return SYSTEM;
    }
}
