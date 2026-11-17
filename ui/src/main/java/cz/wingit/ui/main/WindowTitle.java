package cz.wingit.ui.main;

import java.nio.file.Path;
import java.util.Optional;

import cz.wingit.ui.Messages;

/**
 * Main window title: {@code repository - [path] - WinGit version}, or just the application part
 * when no repository is open. The version is omitted when unknown (e.g. running from the IDE).
 */
final class WindowTitle {

    private WindowTitle() {
    }

    static String of(Optional<Path> repository, String version) {
        String app = version == null || version.isBlank()
                ? Messages.get("mainWindow.title")
                : Messages.format("mainWindow.title.version", version);
        if (repository.isEmpty()) {
            return app;
        }
        Path path = repository.get();
        Path name = path.getFileName();
        return Messages.format("mainWindow.title.repository",
                name != null ? name.toString() : path.toString(), path.toString(), app);
    }
}
