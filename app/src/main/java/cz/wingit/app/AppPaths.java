package cz.wingit.app;

import java.nio.file.Path;

/**
 * Locations of the application's own files.
 */
final class AppPaths {

    private AppPaths() {
    }

    /** {@code %APPDATA%\WinGit} on Windows, {@code ~/.wingit} elsewhere. */
    static Path dataDir() {
        String appData = System.getenv("APPDATA");
        if (appData != null && !appData.isBlank()) {
            return Path.of(appData, "WinGit");
        }
        return Path.of(System.getProperty("user.home"), ".wingit");
    }

    static Path repositoryListFile() {
        return dataDir().resolve("repositories.txt");
    }

    static Path settingsFile() {
        return dataDir().resolve("settings.properties");
    }
}
