package cz.wingit.app;

import java.io.IOException;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;

import cz.wingit.ui.Language;
import cz.wingit.ui.Theme;
import cz.wingit.ui.main.WindowLayout;
import cz.wingit.ui.settings.Appearance;
import cz.wingit.ui.settings.FetchInterval;
import cz.wingit.ui.settings.Preferences;

/**
 * Small persistent user settings, stored as a UTF-8 properties file. Each change is saved at once.
 */
final class AppSettings {

    private static final String LAST_OPENED_REPOSITORY = "lastOpenedRepository";
    private static final String LAST_SELECTED_FILE = "lastSelectedFile";
    private static final String WINDOW_X = "window.x";
    private static final String WINDOW_Y = "window.y";
    private static final String WINDOW_WIDTH = "window.width";
    private static final String WINDOW_HEIGHT = "window.height";
    private static final String WINDOW_MAXIMIZED = "window.maximized";
    private static final String DIVIDER_PREFIX = "window.divider.";
    private static final String LANGUAGE = "ui.language";
    private static final String THEME = "ui.theme";
    private static final String FETCH_ENABLED = "fetch.auto";
    private static final String FETCH_MINUTES = "fetch.intervalMinutes";

    private final Path file;
    private final Properties properties = new Properties();

    private AppSettings(Path file) {
        this.file = file;
    }

    /** Loads settings; a missing or unreadable file gives defaults (settings are not critical). */
    static AppSettings load(Path file) {
        AppSettings settings = new AppSettings(file);
        if (Files.exists(file)) {
            try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                settings.properties.load(reader);
            } catch (IOException | IllegalArgumentException e) {
                settings.properties.clear();
            }
        }
        return settings;
    }

    Optional<Path> lastOpenedRepository() {
        String value = properties.getProperty(LAST_OPENED_REPOSITORY);
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(Path.of(value));
        } catch (InvalidPathException e) {
            return Optional.empty();
        }
    }

    /**
     * Remembers the opened repository ({@code empty} = none) and saves.
     *
     * @throws UncheckedIOException if the file cannot be written
     */
    void setLastOpenedRepository(Optional<Path> repository) {
        String old = properties.getProperty(LAST_OPENED_REPOSITORY);
        String value = repository.map(Path::toString).orElse(null);
        if (value == null ? old == null : value.equals(old)) {
            return;
        }
        if (value == null) {
            properties.remove(LAST_OPENED_REPOSITORY);
        } else {
            properties.setProperty(LAST_OPENED_REPOSITORY, value);
        }
        save();
    }

    /** File selected in the file list of the last opened repository (repository-relative), if any. */
    Optional<String> lastSelectedFile() {
        String value = properties.getProperty(LAST_SELECTED_FILE);
        return value == null || value.isBlank() ? Optional.empty() : Optional.of(value);
    }

    /**
     * Remembers the selected file ({@code empty} = none) and saves.
     *
     * @throws UncheckedIOException if the file cannot be written
     */
    void setLastSelectedFile(Optional<String> file) {
        String old = properties.getProperty(LAST_SELECTED_FILE);
        String value = file.orElse(null);
        if (value == null ? old == null : value.equals(old)) {
            return;
        }
        if (value == null) {
            properties.remove(LAST_SELECTED_FILE);
        } else {
            properties.setProperty(LAST_SELECTED_FILE, value);
        }
        save();
    }

    /** The saved main-window geometry, or empty if none was saved or it is incomplete. */
    Optional<WindowLayout> windowLayout() {
        try {
            String x = properties.getProperty(WINDOW_X);
            String y = properties.getProperty(WINDOW_Y);
            String width = properties.getProperty(WINDOW_WIDTH);
            String height = properties.getProperty(WINDOW_HEIGHT);
            if (x == null || y == null || width == null || height == null) {
                return Optional.empty();
            }
            Map<String, WindowLayout.Divider> dividers = new LinkedHashMap<>();
            for (String key : properties.stringPropertyNames()) {
                if (key.startsWith(DIVIDER_PREFIX)) {
                    dividers.put(key.substring(DIVIDER_PREFIX.length()), parseDivider(properties.getProperty(key)));
                }
            }
            return Optional.of(new WindowLayout(Integer.parseInt(x.trim()), Integer.parseInt(y.trim()),
                    Integer.parseInt(width.trim()), Integer.parseInt(height.trim()),
                    Boolean.parseBoolean(properties.getProperty(WINDOW_MAXIMIZED, "false").trim()), dividers));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }

    /**
     * Remembers the main-window geometry and saves.
     *
     * @throws UncheckedIOException if the file cannot be written
     */
    void setWindowLayout(WindowLayout layout) {
        properties.stringPropertyNames().stream()
                .filter(key -> key.startsWith(DIVIDER_PREFIX))
                .forEach(properties::remove);
        properties.setProperty(WINDOW_X, Integer.toString(layout.x()));
        properties.setProperty(WINDOW_Y, Integer.toString(layout.y()));
        properties.setProperty(WINDOW_WIDTH, Integer.toString(layout.width()));
        properties.setProperty(WINDOW_HEIGHT, Integer.toString(layout.height()));
        properties.setProperty(WINDOW_MAXIMIZED, Boolean.toString(layout.maximized()));
        layout.dividers().forEach((name, divider) ->
                properties.setProperty(DIVIDER_PREFIX + name, divider.location() + "/" + divider.splitSize()));
        save();
    }

    /** Language and theme chosen in the Settings dialog (applied at startup). */
    Appearance appearance() {
        return new Appearance(Language.fromCode(properties.getProperty(LANGUAGE)),
                Theme.Mode.fromCode(properties.getProperty(THEME)));
    }

    /**
     * Remembers the language and theme and saves.
     *
     * @throws UncheckedIOException if the file cannot be written
     */
    void setAppearance(Appearance appearance) {
        properties.setProperty(LANGUAGE, appearance.language().code());
        properties.setProperty(THEME, appearance.theme().code());
        save();
    }

    /** Automatic fetch interval chosen in the Settings dialog; defaults for missing or broken values. */
    FetchInterval fetchInterval() {
        String enabled = properties.getProperty(FETCH_ENABLED);
        int minutes = FetchInterval.DEFAULT.minutes();
        try {
            String stored = properties.getProperty(FETCH_MINUTES);
            if (stored != null) {
                minutes = FetchInterval.clamp(Integer.parseInt(stored.trim()));
            }
        } catch (NumberFormatException e) {
            // keep the default
        }
        return new FetchInterval(enabled == null ? FetchInterval.DEFAULT.enabled() : Boolean.parseBoolean(enabled.trim()),
                minutes);
    }

    /** Everything the Settings dialog edits. */
    Preferences preferences() {
        return new Preferences(appearance(), fetchInterval());
    }

    /**
     * Remembers what the Settings dialog edits and saves.
     *
     * @throws UncheckedIOException if the file cannot be written
     */
    void setPreferences(Preferences preferences) {
        properties.setProperty(LANGUAGE, preferences.appearance().language().code());
        properties.setProperty(THEME, preferences.appearance().theme().code());
        properties.setProperty(FETCH_ENABLED, Boolean.toString(preferences.fetch().enabled()));
        properties.setProperty(FETCH_MINUTES, Integer.toString(preferences.fetch().minutes()));
        save();
    }

    /** {@code location/splitSize}, or just {@code location} (split size unknown). */
    private static WindowLayout.Divider parseDivider(String value) {
        String[] parts = value.trim().split("/", 2);
        int splitSize = parts.length > 1 ? Integer.parseInt(parts[1].trim()) : 0;
        return new WindowLayout.Divider(Integer.parseInt(parts[0].trim()), splitSize);
    }

    private void save() {
        try {
            Path parent = file.toAbsolutePath().getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Path temp = file.resolveSibling(file.getFileName() + ".tmp");
            try (Writer writer = Files.newBufferedWriter(temp, StandardCharsets.UTF_8)) {
                properties.store(writer, "WinGit settings");
            }
            Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot save " + file, e);
        }
    }
}
