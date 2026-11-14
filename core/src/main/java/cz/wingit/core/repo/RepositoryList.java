package cz.wingit.core.repo;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * The user's list of known repositories, persisted as one absolute path per line (UTF-8).
 * Kept sorted by folder name; paths are compared case-insensitively on Windows.
 * Not thread-safe: use from one thread (the EDT).
 */
public final class RepositoryList {

    private static final boolean CASE_INSENSITIVE_FS =
            System.getProperty("os.name", "").toLowerCase(Locale.ROOT).startsWith("windows");

    private static final Comparator<Path> BY_NAME = Comparator
            .comparing((Path p) -> displayName(p).toLowerCase(Locale.ROOT))
            .thenComparing(p -> p.toString().toLowerCase(Locale.ROOT));

    private final Path storeFile;
    private final List<Path> paths = new ArrayList<>();

    private RepositoryList(Path storeFile) {
        this.storeFile = storeFile;
    }

    /** Loads the list from {@code storeFile}; a missing file gives an empty list. */
    public static RepositoryList load(Path storeFile) {
        RepositoryList list = new RepositoryList(storeFile);
        if (Files.exists(storeFile)) {
            try {
                for (String line : Files.readAllLines(storeFile, StandardCharsets.UTF_8)) {
                    if (!line.isBlank()) {
                        list.addInternal(Path.of(line.strip()));
                    }
                }
            } catch (IOException e) {
                throw new UncheckedIOException("Cannot read " + storeFile, e);
            }
        }
        return list;
    }

    /** Snapshot of the paths, sorted by folder name. */
    public List<Path> paths() {
        return List.copyOf(paths);
    }

    public boolean contains(Path path) {
        Path normalized = normalize(path);
        return paths.stream().anyMatch(p -> samePath(p, normalized));
    }

    /**
     * Adds a repository and saves the list.
     *
     * @return {@code false} if it was already present (nothing is saved then)
     * @throws UncheckedIOException if the list cannot be saved
     */
    public boolean add(Path path) {
        if (!addInternal(path)) {
            return false;
        }
        save();
        return true;
    }

    /**
     * Removes a repository from the list (files on disk are untouched) and saves the list.
     *
     * @return {@code false} if it was not in the list (nothing is saved then)
     * @throws UncheckedIOException if the list cannot be saved; the entry stays removed in memory
     */
    public boolean remove(Path path) {
        Path normalized = normalize(path);
        if (!paths.removeIf(p -> samePath(p, normalized))) {
            return false;
        }
        save();
        return true;
    }

    /** Compares two paths the way the file system does (case-insensitive on Windows). */
    public static boolean samePath(Path a, Path b) {
        String left = normalize(a).toString();
        String right = normalize(b).toString();
        return CASE_INSENSITIVE_FS ? left.equalsIgnoreCase(right) : left.equals(right);
    }

    private boolean addInternal(Path path) {
        if (contains(path)) {
            return false;
        }
        paths.add(normalize(path));
        paths.sort(BY_NAME);
        return true;
    }

    private void save() {
        try {
            Path parent = storeFile.toAbsolutePath().getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            List<String> lines = paths.stream().map(Path::toString).toList();
            Path temp = storeFile.resolveSibling(storeFile.getFileName() + ".tmp");
            Files.write(temp, lines, StandardCharsets.UTF_8);
            Files.move(temp, storeFile, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot save " + storeFile, e);
        }
    }

    private static Path normalize(Path path) {
        return path.toAbsolutePath().normalize();
    }

    private static String displayName(Path path) {
        Path name = path.getFileName();
        return name != null ? name.toString() : path.toString();
    }
}
