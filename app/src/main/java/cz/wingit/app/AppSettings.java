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
import java.util.Optional;
import java.util.Properties;

/**
 * Small persistent user settings, stored as a UTF-8 properties file. Each change is saved at once.
 */
final class AppSettings {

    private static final String LAST_OPENED_REPOSITORY = "lastOpenedRepository";

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
