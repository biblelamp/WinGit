package cz.wingit.core;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.stream.Stream;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.ObjectId;

import cz.wingit.core.cli.GitCli;
import cz.wingit.core.repo.Repositories;

/**
 * {@link GitService} that reads with JGit and writes through the {@code git} CLI.
 */
public final class DefaultGitService implements GitService {

    private static final int ABBREV_LENGTH = 7;

    private final GitCli cli;

    public DefaultGitService(GitCli cli) {
        this.cli = cli;
    }

    @Override
    public boolean isWorkTree(Path dir) {
        return Repositories.isWorkTree(dir);
    }

    @Override
    public Optional<Path> findWorkTreeRoot(Path dir) {
        return Repositories.findWorkTreeRoot(dir);
    }

    @Override
    public Optional<String> currentBranch(Path workTree) {
        try (Git git = Git.open(workTree.toFile())) {
            String branch = git.getRepository().getBranch();
            if (branch == null) {
                return Optional.empty();
            }
            if (ObjectId.isId(branch)) {
                return Optional.of(branch.substring(0, ABBREV_LENGTH));
            }
            return Optional.of(branch);
        } catch (IOException e) {
            return Optional.empty();
        }
    }

    @Override
    public void init(Path dir) {
        cli.run(dir, List.of("init"));
    }

    @Override
    public void clone(String url, Path target, Consumer<String> progress) {
        Path absolute = target.toAbsolutePath().normalize();
        Path parent = absolute.getParent();
        if (parent == null) {
            throw new IllegalArgumentException("Cannot clone into a filesystem root: " + absolute);
        }
        boolean existed = Files.exists(absolute);
        try {
            Files.createDirectories(parent);
        } catch (IOException e) {
            throw new GitException("Cannot create folder " + parent + ": " + e.getMessage(), e);
        }
        try {
            cli.run(parent, List.of("clone", "--progress", "--", url, absolute.toString()), progress);
        } catch (GitException e) {
            if (!existed) {
                deleteQuietly(absolute);
            }
            throw e;
        }
    }

    /** Removes a partially cloned directory; Git marks pack files read-only on Windows. */
    private static void deleteQuietly(Path dir) {
        if (!Files.exists(dir)) {
            return;
        }
        try (Stream<Path> walk = Files.walk(dir)) {
            walk.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    path.toFile().setWritable(true);
                    Files.deleteIfExists(path);
                } catch (IOException e) {
                    // best effort; leftovers are reported by the next clone attempt
                }
            });
        } catch (IOException e) {
            // best effort
        }
    }
}
