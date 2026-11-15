package cz.wingit.core;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Stream;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.ConfigConstants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.RepositoryState;
import org.eclipse.jgit.lib.StoredConfig;

import cz.wingit.core.cli.GitCli;
import cz.wingit.core.diff.DiffReader;
import cz.wingit.core.diff.FileDiff;
import cz.wingit.core.log.CommitInfo;
import cz.wingit.core.log.LogReader;
import cz.wingit.core.refs.RefsReader;
import cz.wingit.core.refs.RepositoryRefs;
import cz.wingit.core.remote.AskPass;
import cz.wingit.core.remote.AuthFailures;
import cz.wingit.core.remote.Credentials;
import cz.wingit.core.remote.RemoteTarget;
import cz.wingit.core.repo.Repositories;
import cz.wingit.core.status.FileChange;
import cz.wingit.core.status.RepositoryStatus;
import cz.wingit.core.status.StatusMapper;
import cz.wingit.core.watch.RepositoryWatch;
import cz.wingit.core.watch.RepositoryWatcher;
import cz.wingit.core.watch.RepositoryWatches;

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
    public RepositoryStatus status(Path workTree) {
        try (Git git = Git.open(workTree.toFile())) {
            return StatusMapper.from(git.status().call());
        } catch (IOException | GitAPIException e) {
            throw new GitException("Cannot read status of " + workTree + ": " + e.getMessage(), e);
        }
    }

    @Override
    public FileDiff fileDiff(Path workTree, FileChange change) {
        try (Git git = Git.open(workTree.toFile())) {
            return DiffReader.read(git.getRepository(), change);
        } catch (IOException e) {
            throw new GitException("Cannot read " + change.path() + ": " + e.getMessage(), e);
        }
    }

    @Override
    public RepositoryRefs refs(Path workTree) {
        try (Git git = Git.open(workTree.toFile())) {
            return RefsReader.read(git);
        } catch (IOException | GitAPIException e) {
            throw new GitException("Cannot read branches of " + workTree + ": " + e.getMessage(), e);
        }
    }

    @Override
    public List<CommitInfo> recentCommits(Path workTree, int limit) {
        try (Git git = Git.open(workTree.toFile())) {
            return LogReader.recent(git.getRepository(), limit);
        } catch (IOException e) {
            throw new GitException("Cannot read history of " + workTree + ": " + e.getMessage(), e);
        }
    }

    @Override
    public List<String> recentMessages(Path workTree, int limit) {
        try (Git git = Git.open(workTree.toFile())) {
            return LogReader.recentMessages(git.getRepository(), limit);
        } catch (IOException e) {
            throw new GitException("Cannot read history of " + workTree + ": " + e.getMessage(), e);
        }
    }

    @Override
    public int unpushedCount(Path workTree, int max) {
        try (Git git = Git.open(workTree.toFile())) {
            return LogReader.unpushedCount(git.getRepository(), max);
        } catch (IOException e) {
            throw new GitException("Cannot read history of " + workTree + ": " + e.getMessage(), e);
        }
    }

    @Override
    public RepositoryWatch watch(Path workTree, Runnable onChange) {
        try {
            return RepositoryWatches.start(workTree, RepositoryWatcher.DEFAULT_DEBOUNCE, onChange);
        } catch (IOException e) {
            throw new GitException("Cannot watch " + workTree + ": " + e.getMessage(), e);
        }
    }

    @Override
    public void switchBranch(Path workTree, String branch) {
        cli.run(workTree, List.of("switch", "--no-guess", branch));
    }

    @Override
    public void switchToNewTrackingBranch(Path workTree, String remote, String branch) {
        cli.run(workTree, List.of("switch", "--create", branch, "--track", remote + "/" + branch));
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

    @Override
    public void commit(Path workTree, List<FileChange> changes, String message, Instant date) {
        if (changes.isEmpty()) {
            throw new IllegalArgumentException("Nothing selected to commit");
        }
        if (message.isBlank()) {
            throw new IllegalArgumentException("Commit message is empty");
        }
        // Files whose change is only in the index (e.g. a staged deletion) are not in the working
        // tree any more and would make 'git add' fail; they are committed as staged.
        List<String> toAdd = new ArrayList<>();
        List<String> paths = new ArrayList<>();
        for (FileChange change : changes) {
            paths.add(change.path());
            if (change.unstaged() != null) {
                toAdd.add(change.path());
            }
        }
        if (!toAdd.isEmpty()) {
            cli.run(workTree, List.of("--literal-pathspecs", "add", "--all",
                    "--pathspec-from-file=-", "--pathspec-file-nul"), null, Map.of(), nulSeparated(toAdd));
        }
        Map<String, String> environment = date == null ? Map.of() : Map.of(
                "GIT_AUTHOR_DATE", gitDate(date, ZoneId.systemDefault()),
                "GIT_COMMITTER_DATE", gitDate(date, ZoneId.systemDefault()));
        if (readRepository(workTree, r -> r.getRepositoryState() != RepositoryState.SAFE)) {
            cli.run(workTree, List.of("commit", "--file=-"), null, environment, message);
            return;
        }
        Path pathspecFile = null;
        try {
            pathspecFile = Files.createTempFile("wingit-commit-", ".paths");
            Files.writeString(pathspecFile, nulSeparated(paths), StandardCharsets.UTF_8);
            cli.run(workTree, List.of("--literal-pathspecs", "commit", "--file=-",
                    "--pathspec-from-file=" + pathspecFile, "--pathspec-file-nul"), null, environment, message);
        } catch (IOException e) {
            throw new GitException("Cannot prepare the commit: " + e.getMessage(), e);
        } finally {
            if (pathspecFile != null) {
                try {
                    Files.deleteIfExists(pathspecFile);
                } catch (IOException ignored) {
                    // temp file, removed by the OS eventually
                }
            }
        }
    }

    @Override
    public void push(Path workTree, Credentials credentials, Consumer<String> progress) {
        RemoteTarget target = readRepository(workTree, RemoteTarget::forPush);
        String source = "refs/heads/" + target.branch();
        List<String> args = new ArrayList<>(List.of("push", "--progress"));
        if (target.hasUpstream()) {
            args.addAll(List.of(target.remote(), source + ":" + target.remoteRef()));
        } else {
            args.addAll(List.of("--set-upstream", target.remote(), source + ":" + source));
        }
        runRemote(workTree, args, target.url(), credentials, progress);
    }

    @Override
    public void pull(Path workTree, Credentials credentials, Consumer<String> progress) {
        boolean[] rebaseConfigured = new boolean[1];
        RemoteTarget target = readRepository(workTree, repository -> {
            RemoteTarget t = RemoteTarget.forPull(repository);
            StoredConfig config = repository.getConfig();
            rebaseConfigured[0] = config.getString("pull", null, "rebase") != null
                    || config.getString(ConfigConstants.CONFIG_BRANCH_SECTION, t.branch(), "rebase") != null;
            return t;
        });
        List<String> args = new ArrayList<>(List.of("pull", "--progress", "--no-edit"));
        if (!rebaseConfigured[0]) {
            // Without pull.rebase Git refuses to reconcile diverged branches; merge like Git used to.
            args.add("--no-rebase");
        }
        runRemote(workTree, args, target.url(), credentials, progress);
    }

    @Override
    public void storeCredentials(Path workTree, String url, Credentials credentials) {
        String input = "url=" + singleLine(url) + "\n"
                + "username=" + singleLine(credentials.username()) + "\n"
                + "password=" + singleLine(credentials.password()) + "\n\n";
        cli.run(workTree, List.of("credential", "approve"), null, Map.of(), input);
    }

    private void runRemote(Path workTree, List<String> args, String url, Credentials credentials,
                           Consumer<String> progress) {
        List<String> command = credentials == null ? args : AskPass.withOptions(args);
        Map<String, String> environment = credentials == null
                ? AskPass.storedOnlyEnvironment() : AskPass.environment(credentials);
        try {
            cli.run(workTree, command, progress, environment, null);
        } catch (GitCommandException e) {
            if (AuthFailures.isAuthenticationFailure(e.stderr())) {
                throw new GitAuthenticationException(url, e.stderr(), e);
            }
            throw e;
        }
    }

    private static <T> T readRepository(Path workTree, Function<Repository, T> reader) {
        try (Git git = Git.open(workTree.toFile())) {
            return reader.apply(git.getRepository());
        } catch (IOException e) {
            throw new GitException("Cannot open repository " + workTree + ": " + e.getMessage(), e);
        }
    }

    /** Git's internal date format, e.g. {@code 1700000000 +0100}; unambiguous for any locale. */
    static String gitDate(Instant date, ZoneId zone) {
        ZoneOffset offset = zone.getRules().getOffset(date);
        int minutes = Math.abs(offset.getTotalSeconds()) / 60;
        return String.format("%d %s%02d%02d", date.getEpochSecond(),
                offset.getTotalSeconds() < 0 ? "-" : "+", minutes / 60, minutes % 60);
    }

    private static String nulSeparated(List<String> paths) {
        return String.join("\0", paths);
    }

    /** Git's credential protocol is line based; a line break would inject extra attributes. */
    private static String singleLine(String value) {
        if (value.indexOf('\n') >= 0 || value.indexOf('\r') >= 0 || value.indexOf('\0') >= 0) {
            throw new IllegalArgumentException("Credentials must not contain line breaks");
        }
        return value;
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
