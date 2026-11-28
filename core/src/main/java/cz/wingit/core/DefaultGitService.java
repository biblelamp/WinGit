package cz.wingit.core;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.Status;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.ConfigConstants;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.RepositoryState;
import org.eclipse.jgit.lib.StoredConfig;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevObject;
import org.eclipse.jgit.revwalk.RevTag;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.eclipse.jgit.util.SystemReader;

import cz.wingit.core.cli.GitCli;
import cz.wingit.core.cli.GitResult;
import cz.wingit.core.cli.LoggedCommand;
import cz.wingit.core.diff.DiffReader;
import cz.wingit.core.diff.FileDiff;
import cz.wingit.core.log.CommitInfo;
import cz.wingit.core.log.CommitDetails;
import cz.wingit.core.log.CommitFile;
import cz.wingit.core.log.CommitReader;
import cz.wingit.core.log.HistoryReader;
import cz.wingit.core.log.HistoryVersion;
import cz.wingit.core.log.LogReader;
import cz.wingit.core.merge.MergeConflicts;
import cz.wingit.core.merge.MergePreview;
import cz.wingit.core.merge.MergePreviewReader;
import cz.wingit.core.merge.MergeResult;
import cz.wingit.core.refs.BranchNames;
import cz.wingit.core.refs.RefsReader;
import cz.wingit.core.refs.RepositoryRefs;
import cz.wingit.core.refs.Stash;
import cz.wingit.core.refs.StashedSwitch;
import cz.wingit.core.refs.SwitchClash;
import cz.wingit.core.refs.TagNames;
import cz.wingit.core.remote.AskPass;
import cz.wingit.core.remote.AuthFailures;
import cz.wingit.core.remote.Credentials;
import cz.wingit.core.remote.RemoteTarget;
import cz.wingit.core.remote.SshCommand;
import cz.wingit.core.repo.Repositories;
import cz.wingit.core.status.ChangeType;
import cz.wingit.core.status.FileChange;
import cz.wingit.core.status.RepositoryStatus;
import cz.wingit.core.status.StagedRenames;
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
    /** Private key for SSH remotes of repositories without their own ({@code null}: ssh's defaults). */
    private volatile Path sshKey;

    public DefaultGitService(GitCli cli) {
        this.cli = cli;
    }

    @Override
    public void setSshKey(Optional<Path> key) {
        sshKey = key.orElse(null);
    }

    @Override
    public Optional<String> repositorySshCommand(Path workTree) {
        try {
            String out = cli.run(workTree, List.of("config", "--local", "--get", "core.sshCommand")).stdout();
            String command = out.strip();
            return command.isEmpty() ? Optional.empty() : Optional.of(command);
        } catch (GitCommandException e) {
            if (e.exitCode() == 1) {
                return Optional.empty(); // not set
            }
            throw e;
        }
    }

    @Override
    public void setRepositorySshKey(Path workTree, Optional<Path> key) {
        if (key.isPresent()) {
            cli.run(workTree, List.of("config", "--local", "core.sshCommand", SshCommand.forKey(key.get(), false)));
            return;
        }
        try {
            cli.run(workTree, List.of("config", "--local", "--unset-all", "core.sshCommand"));
        } catch (GitCommandException e) {
            if (e.exitCode() != 5) { // 5: was not set
                throw e;
            }
        }
    }

    @Override
    public void setRepositoryIdentity(Path workTree, String name, String email) {
        setLocalConfig(workTree, "user.name", name);
        setLocalConfig(workTree, "user.email", email);
    }

    /** {@code git config --local key value}; a blank value removes the key (nothing to remove is fine). */
    private void setLocalConfig(Path workTree, String key, String value) {
        if (value != null && !value.isBlank()) {
            cli.run(workTree, List.of("config", "--local", key, value.strip()));
            return;
        }
        try {
            cli.run(workTree, List.of("config", "--local", "--unset-all", key));
        } catch (GitCommandException e) {
            if (e.exitCode() != 5) { // 5: was not set
                throw e;
            }
        }
    }

    @Override
    public Optional<String> remoteUrl(Path workTree, String remote) {
        return readRepository(workTree, r -> Optional.ofNullable(r.getConfig()
                .getString(ConfigConstants.CONFIG_REMOTE_SECTION, remote, ConfigConstants.CONFIG_KEY_URL)));
    }

    @Override
    public void setRemoteUrl(Path workTree, String remote, String url) {
        String trimmed = url == null ? "" : url.strip();
        if (trimmed.isEmpty() || trimmed.startsWith("-")) {
            throw new IllegalArgumentException("Not a remote URL: " + url);
        }
        boolean exists = readRepository(workTree, r -> r.getConfig()
                .getSubsections(ConfigConstants.CONFIG_REMOTE_SECTION).contains(remote));
        cli.run(workTree, exists ? List.of("remote", "set-url", remote, trimmed)
                : List.of("remote", "add", remote, trimmed));
    }

    /**
     * {@code GIT_SSH_COMMAND} with the key chosen in WinGit's settings and, for {@code batch}, without any
     * prompt; nothing when the user configured SSH themselves ({@code core.sshCommand}, {@code GIT_SSH...}),
     * which then wins as in plain Git.
     */
    Map<String, String> sshEnvironment(boolean configured, boolean batch) {
        Path key = sshKey;
        if (configured || (key == null && !batch)) {
            return Map.of();
        }
        return Map.of("GIT_SSH_COMMAND", SshCommand.forKey(key, batch));
    }

    /** Whether {@code config} (or the environment) sets the SSH command Git runs. */
    static boolean sshConfigured(Config config) {
        return (config != null && config.getString(ConfigConstants.CONFIG_CORE_SECTION, null, "sshCommand") != null)
                || System.getenv("GIT_SSH_COMMAND") != null || System.getenv("GIT_SSH") != null;
    }

    /** {@link #sshConfigured} for the global and system configuration, when there is no repository yet. */
    private static boolean sshConfiguredOutsideRepositories() {
        try {
            return sshConfigured(SystemReader.getInstance().getUserConfig());
        } catch (IOException | ConfigInvalidException e) {
            return sshConfigured(null);
        }
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
            Status status = git.status().call();
            return StatusMapper.from(status, StagedRenames.find(git.getRepository(), status));
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
    public String historyVersion(Path workTree) {
        try (Git git = Git.open(workTree.toFile())) {
            return HistoryVersion.of(git.getRepository());
        } catch (IOException e) {
            throw new GitException("Cannot read branches of " + workTree + ": " + e.getMessage(), e);
        }
    }

    @Override
    public HistoryReader openHistory(Path workTree, String revision) {
        Git git;
        try {
            git = Git.open(workTree.toFile());
        } catch (IOException e) {
            throw new GitException("Cannot read history of " + workTree + ": " + e.getMessage(), e);
        }
        try {
            return HistoryReader.open(git.getRepository(), revision); // closes the repository on failure
        } catch (IOException e) {
            throw new GitException("Cannot read history of " + revision + ": " + e.getMessage(), e);
        }
    }

    @Override
    public CommitDetails commitDetails(Path workTree, String id) {
        try (Git git = Git.open(workTree.toFile())) {
            return CommitReader.read(git.getRepository(), id);
        } catch (IOException e) {
            throw new GitException("Cannot read commit " + id + ": " + e.getMessage(), e);
        }
    }

    @Override
    public FileDiff commitFileDiff(Path workTree, String id, CommitFile file) {
        try (Git git = Git.open(workTree.toFile())) {
            return DiffReader.readCommit(git.getRepository(), id, file);
        } catch (IOException e) {
            throw new GitException("Cannot read " + file.path() + ": " + e.getMessage(), e);
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
    public int unpulledCount(Path workTree, int max) {
        try (Git git = Git.open(workTree.toFile())) {
            return LogReader.unpulledCount(git.getRepository(), max);
        } catch (IOException e) {
            throw new GitException("Cannot read history of " + workTree + ": " + e.getMessage(), e);
        }
    }

    @Override
    public String gitVersion() {
        // any existing folder will do: --version does not look at a repository
        String out = cli.run(Path.of(System.getProperty("user.home")), List.of("--version")).stdout().strip();
        return parseGitVersion(out);
    }

    @Override
    public GitIdentity globalIdentity() {
        return readGlobalIdentity(cli, Map.of());
    }

    /** {@code environment} lets tests point {@code GIT_CONFIG_GLOBAL} at their own file. */
    static GitIdentity readGlobalIdentity(GitCli cli, Map<String, String> environment) {
        try {
            String out = cli.run(Path.of(System.getProperty("user.home")),
                    List.of("config", "--global", "--includes", "-z", "--get-regexp", "^user\\.(name|email)$"),
                    null, environment, null).stdout();
            return parseIdentity(out);
        } catch (GitCommandException e) {
            if (e.exitCode() == 1) {
                return GitIdentity.NONE; // no such key, or no global configuration file at all
            }
            throw e;
        }
    }

    /** {@code key<LF>value<NUL>} entries of {@code git config -z}; a later value wins, as in Git. */
    static GitIdentity parseIdentity(String output) {
        String name = "";
        String email = "";
        for (String entry : output.split("\0")) {
            int newline = entry.indexOf('\n');
            String key = (newline < 0 ? entry : entry.substring(0, newline)).strip().toLowerCase(Locale.ROOT);
            String value = newline < 0 ? "" : entry.substring(newline + 1).strip();
            if (key.equals("user.name")) {
                name = value;
            } else if (key.equals("user.email")) {
                email = value;
            }
        }
        return new GitIdentity(name, email);
    }

    @Override
    public RepositoryIdentity repositoryIdentity(Path workTree) {
        return readRepositoryIdentity(cli, workTree, Map.of());
    }

    /** {@code environment} lets tests point {@code GIT_CONFIG_GLOBAL} at their own file. */
    static RepositoryIdentity readRepositoryIdentity(GitCli cli, Path workTree, Map<String, String> environment) {
        try {
            // No scope option: all levels as Git uses them for a commit here (system, global, includeIf, local).
            String out = cli.run(workTree,
                    List.of("config", "-z", "--show-scope", "--get-regexp", "^user\\.(name|email)$"),
                    null, environment, null).stdout();
            return parseScopedIdentity(out);
        } catch (GitCommandException e) {
            if (e.exitCode() == 1) {
                return new RepositoryIdentity(GitIdentity.NONE, false, false);
            }
            throw e;
        }
    }

    /**
     * {@code scope<NUL>key<LF>value<NUL>} entries of {@code git config -z --show-scope}; a later value wins.
     * Values from the {@code local} or {@code worktree} scope belong to the repository itself.
     */
    static RepositoryIdentity parseScopedIdentity(String output) {
        String name = "";
        String email = "";
        boolean ownName = false;
        boolean ownEmail = false;
        String[] tokens = output.split("\0");
        for (int i = 0; i + 1 < tokens.length; i += 2) {
            String scope = tokens[i].strip().toLowerCase(Locale.ROOT);
            boolean own = scope.equals("local") || scope.equals("worktree");
            String entry = tokens[i + 1];
            int newline = entry.indexOf('\n');
            String key = (newline < 0 ? entry : entry.substring(0, newline)).strip().toLowerCase(Locale.ROOT);
            String value = newline < 0 ? "" : entry.substring(newline + 1).strip();
            if (key.equals("user.name")) {
                name = value;
                ownName = own;
            } else if (key.equals("user.email")) {
                email = value;
                ownEmail = own;
            }
        }
        return new RepositoryIdentity(new GitIdentity(name, email), ownName, ownEmail);
    }

    /** {@code git version 2.47.1.windows.1} -> {@code 2.47.1.windows.1}; other text is returned as is. */
    static String parseGitVersion(String output) {
        String prefix = "git version ";
        String line = output.lines().findFirst().orElse("").strip();
        return line.startsWith(prefix) ? line.substring(prefix.length()).strip() : line;
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
    public MergeResult merge(Path workTree, String ref, boolean noFastForward) {
        if (ref.isEmpty() || ref.startsWith("-")) {
            throw new IllegalArgumentException("Not a branch: " + ref);
        }
        ObjectId before = readRepository(workTree, DefaultGitService::headId);
        try {
            cli.run(workTree, List.of("merge", "--no-edit", noFastForward ? "--no-ff" : "--ff", ref));
        } catch (GitCommandException e) {
            if (!mergeInProgress(workTree)) {
                throw e;
            }
            List<String> conflicts = status(workTree).changes().stream()
                    .filter(c -> c.staged() == ChangeType.CONFLICTED || c.unstaged() == ChangeType.CONFLICTED)
                    .map(FileChange::path).toList();
            return new MergeResult(MergeResult.Outcome.CONFLICTS, conflicts);
        }
        return readRepository(workTree, repository -> {
            ObjectId after = headId(repository);
            if (after == null || after.equals(before)) {
                return MergeResult.of(MergeResult.Outcome.UP_TO_DATE);
            }
            try (RevWalk walk = new RevWalk(repository)) {
                return MergeResult.of(walk.parseCommit(after).getParentCount() > 1
                        ? MergeResult.Outcome.MERGED : MergeResult.Outcome.FAST_FORWARD);
            } catch (IOException e) {
                throw new GitException("Cannot read the merge result: " + e.getMessage(), e);
            }
        });
    }

    @Override
    public MergePreview mergePreview(Path workTree, String ref, int limit) {
        int localChanges = (int) status(workTree).changes().stream()
                .filter(c -> c.staged() != null || (c.unstaged() != null && c.unstaged() != ChangeType.UNTRACKED))
                .count();
        try (Git git = Git.open(workTree.toFile())) {
            return MergePreviewReader.read(git.getRepository(), ref, limit, localChanges);
        } catch (IOException e) {
            throw new GitException("Cannot read history of " + workTree + ": " + e.getMessage(), e);
        }
    }

    @Override
    public MergeConflicts mergeConflicts(Path workTree, String ref) {
        if (ref.isEmpty() || ref.startsWith("-")) {
            throw new IllegalArgumentException("Not a branch: " + ref);
        }
        boolean hasHead = readRepository(workTree, DefaultGitService::headId) != null;
        if (!hasHead) {
            return new MergeConflicts(MergeConflicts.Certainty.EXACT, List.of()); // nothing to conflict with
        }
        GitResult result;
        try {
            result = cli.runAllowing(workTree,
                    List.of("merge-tree", "--write-tree", "--name-only", "--no-messages", "-z", "HEAD", ref), 1);
        } catch (GitCommandException e) {
            // Git before 2.38 knows no --write-tree (usage error 129); also unrelated histories end here
            try (Git git = Git.open(workTree.toFile())) {
                return new MergeConflicts(MergeConflicts.Certainty.LIKELY,
                        MergePreviewReader.changedOnBothSides(git.getRepository(), ref));
            } catch (IOException io) {
                throw new GitException("Cannot read history of " + workTree + ": " + io.getMessage(), io);
            }
        }
        return new MergeConflicts(MergeConflicts.Certainty.EXACT,
                result.exitCode() == 0 ? List.of() : parseMergeTreeConflicts(result.stdout()));
    }

    /**
     * {@code <tree id>NUL<path>NUL<path>NUL...} of {@code git merge-tree --write-tree --name-only -z}:
     * the conflicting paths, each once, in Git's order.
     */
    static List<String> parseMergeTreeConflicts(String output) {
        String[] tokens = output.split("\0");
        Set<String> paths = new LinkedHashSet<>();
        for (int i = 1; i < tokens.length; i++) {
            if (!tokens[i].isEmpty()) {
                paths.add(tokens[i]);
            }
        }
        return List.copyOf(paths);
    }

    @Override
    public boolean mergeInProgress(Path workTree) {
        return readRepository(workTree, repository -> {
            RepositoryState state = repository.getRepositoryState();
            return state == RepositoryState.MERGING || state == RepositoryState.MERGING_RESOLVED;
        });
    }

    @Override
    public Optional<String> mergeMessage(Path workTree) {
        return readRepository(workTree, repository -> {
            try {
                String message = repository.readMergeCommitMsg();
                return message == null || message.isBlank() ? Optional.<String>empty()
                        : Optional.of(stripComments(message));
            } catch (IOException e) {
                return Optional.<String>empty();
            }
        });
    }

    /** Removes Git's "#" comment lines (e.g. the list of conflicts) from a prepared message. */
    static String stripComments(String message) {
        return message.lines().filter(line -> !line.startsWith("#"))
                .collect(Collectors.joining("\n")).strip();
    }

    @Override
    public void abortMerge(Path workTree) {
        cli.run(workTree, List.of("merge", "--abort"));
    }

    private static ObjectId headId(Repository repository) {
        try {
            return repository.resolve(Constants.HEAD);
        } catch (IOException e) {
            throw new GitException("Cannot read HEAD: " + e.getMessage(), e);
        }
    }

    @Override
    public void createBranch(Path workTree, String name, boolean checkout) {
        if (!BranchNames.isValid(name)) {
            throw new IllegalArgumentException("Not a valid branch name: " + name);
        }
        cli.run(workTree, checkout ? List.of("switch", "--create", name) : List.of("branch", "--", name));
    }

    @Override
    public void renameBranch(Path workTree, String name, String newName) {
        if (!BranchNames.isValid(newName)) {
            throw new IllegalArgumentException("Not a valid branch name: " + newName);
        }
        // -M only for a case change ("feature" -> "Feature"), which Windows sees as the same ref file.
        String option = name.equalsIgnoreCase(newName) && !name.equals(newName) ? "-M" : "-m";
        cli.run(workTree, List.of("branch", option, "--", name, newName));
    }

    @Override
    public void deleteBranch(Path workTree, String name, boolean force) {
        try {
            cli.run(workTree, List.of("branch", force ? "-D" : "-d", "--", name));
        } catch (GitCommandException e) {
            if (!force && e.stderr().contains("not fully merged")) {
                throw new GitBranchNotMergedException(name, e);
            }
            throw e;
        }
    }

    @Override
    public void createTag(Path workTree, String name, String commit, String message) {
        if (!TagNames.isValid(name)) {
            throw new IllegalArgumentException("Not a valid tag name: " + name);
        }
        if (commit != null && !commit.matches("[0-9a-fA-F]{4,64}")) {
            throw new IllegalArgumentException("Not a commit id: " + commit);
        }
        List<String> args = new ArrayList<>(List.of("tag", "--annotate", "--cleanup=whitespace", "--file=-", name));
        if (commit != null) {
            args.add(commit);
        }
        String text = message == null || message.isBlank() ? name : message.strip();
        cli.run(workTree, args, null, Map.of(), text + "\n");
    }

    @Override
    public void deleteTag(Path workTree, String name) {
        cli.run(workTree, List.of("tag", "--delete", name));
    }

    @Override
    public void renameTag(Path workTree, String name, String newName) {
        if (!TagNames.isValid(newName)) {
            throw new IllegalArgumentException("Not a valid tag name: " + newName);
        }
        TagCopy copy = readRepository(workTree, repository -> TagCopy.read(repository, name));
        if (name.equalsIgnoreCase(newName)) {
            // Windows keeps "v1" and "V1" in one ref file: delete first, restore the old tag if needed.
            cli.run(workTree, List.of("tag", "--delete", name));
            try {
                createCopy(workTree, copy, newName);
            } catch (RuntimeException e) {
                createCopy(workTree, copy, name);
                throw e;
            }
            return;
        }
        createCopy(workTree, copy, newName);
        cli.run(workTree, List.of("tag", "--delete", name));
    }

    /** What makes up a tag, so it can be recreated under another name. */
    private record TagCopy(String target, String message, PersonIdent tagger) {

        static TagCopy read(Repository repository, String name) {
            try (RevWalk walk = new RevWalk(repository)) {
                Ref ref = repository.exactRef(Constants.R_TAGS + name);
                if (ref == null) {
                    throw new GitException("Tag '" + name + "' does not exist.");
                }
                RevObject object = walk.parseAny(ref.getObjectId());
                if (object instanceof RevTag tag) {
                    return new TagCopy(tag.getObject().name(), tag.getFullMessage(), tag.getTaggerIdent());
                }
                return new TagCopy(object.name(), null, null); // lightweight
            } catch (IOException e) {
                throw new GitException("Cannot read tag '" + name + "': " + e.getMessage(), e);
            }
        }
    }

    private void createCopy(Path workTree, TagCopy copy, String name) {
        if (copy.message() == null) {
            cli.run(workTree, List.of("tag", name, copy.target()));
            return;
        }
        Map<String, String> environment = Map.of();
        if (copy.tagger() != null) {
            // Git takes the tagger from the committer identity.
            PersonIdent tagger = copy.tagger();
            environment = Map.of("GIT_COMMITTER_NAME", tagger.getName(),
                    "GIT_COMMITTER_EMAIL", tagger.getEmailAddress(),
                    "GIT_COMMITTER_DATE", gitDate(tagger.getWhenAsInstant(), tagger.getZoneId()));
        }
        cli.run(workTree, List.of("tag", "--annotate", "--cleanup=verbatim", "--file=-", name, copy.target()),
                null, environment, copy.message());
    }

    @Override
    public void renameRemoteTag(Path workTree, String remote, String name, String newName,
                                Credentials credentials, Consumer<String> progress) {
        String url = readRepository(workTree, repository -> RemoteTarget.pushUrl(repository, remote));
        String oldRef = Constants.R_TAGS + name;
        String newRef = Constants.R_TAGS + newName;
        boolean onServer = !runRemote(workTree, List.of("ls-remote", "--tags", remote, oldRef), url, credentials,
                progress).stdout().isBlank();
        List<String> args = new ArrayList<>(List.of("push", "--progress", "--atomic",
                // fails (nothing changes) if the new name is already taken on the server
                "--force-with-lease=" + newRef + ":", remote, newRef + ":" + newRef));
        if (onServer) {
            args.add(":" + oldRef);
        }
        runRemote(workTree, args, url, credentials, progress);
    }

    @Override
    public Optional<String> defaultRemote(Path workTree) {
        return readRepository(workTree, RemoteTarget::defaultRemote);
    }

    @Override
    public void pushTag(Path workTree, String remote, String name, Credentials credentials,
                        Consumer<String> progress) {
        String url = readRepository(workTree, repository -> RemoteTarget.pushUrl(repository, remote));
        String ref = Constants.R_TAGS + name;
        runRemote(workTree, List.of("push", "--progress", remote, ref + ":" + ref), url, credentials, progress);
    }

    @Override
    public void deleteRemoteTag(Path workTree, String remote, String name, Credentials credentials,
                                Consumer<String> progress) {
        String url = readRepository(workTree, repository -> RemoteTarget.pushUrl(repository, remote));
        runRemote(workTree, List.of("push", "--progress", remote, "--delete", Constants.R_TAGS + name),
                url, credentials, progress);
    }

    @Override
    public boolean stash(Path workTree, String message, boolean includeUntracked) {
        ObjectId before = readRepository(workTree, DefaultGitService::stashId);
        List<String> args = new ArrayList<>(List.of("stash", "push"));
        if (includeUntracked) {
            args.add("--include-untracked");
        }
        String text = message == null ? "" : message.strip().replaceAll("\\s*\\R\\s*", " ");
        if (!text.isEmpty()) {
            args.add("--message=" + text);
        }
        cli.run(workTree, args);
        ObjectId after = readRepository(workTree, DefaultGitService::stashId);
        return after != null && !after.equals(before);
    }

    @Override
    public List<String> applyStash(Path workTree, Stash stash, boolean drop) {
        checkStash(workTree, stash);
        try {
            cli.run(workTree, List.of("stash", drop ? "pop" : "apply", stash.ref()));
            return List.of();
        } catch (GitCommandException e) {
            List<String> conflicts = conflicts(workTree);
            if (conflicts.isEmpty()) {
                throw e;
            }
            return conflicts;
        }
    }

    @Override
    public void dropStash(Path workTree, Stash stash) {
        checkStash(workTree, stash);
        cli.run(workTree, List.of("stash", "drop", stash.ref()));
    }

    /** Fails unless {@code stash} is still at its index, so a changed list never hits another stash. */
    private static void checkStash(Path workTree, Stash stash) {
        boolean same;
        try (Git git = Git.open(workTree.toFile())) {
            List<String> ids = new ArrayList<>();
            git.stashList().call().forEach(commit -> ids.add(commit.name()));
            same = stash.index() >= 0 && stash.index() < ids.size() && ids.get(stash.index()).equals(stash.id());
        } catch (IOException | GitAPIException e) {
            throw new GitException("Cannot read the stashes of " + workTree + ": " + e.getMessage(), e);
        }
        if (!same) {
            throw new GitException("The list of stashes has changed in the meantime. Look at it again and retry.");
        }
    }

    private static ObjectId stashId(Repository repository) {
        try {
            Ref ref = repository.exactRef(Constants.R_STASH);
            return ref == null ? null : ref.getObjectId();
        } catch (IOException e) {
            throw new GitException("Cannot read the stashes: " + e.getMessage(), e);
        }
    }

    @Override
    public void switchBranch(Path workTree, String branch) {
        cli.run(workTree, switchArgs(branch, null));
    }

    @Override
    public void switchToNewTrackingBranch(Path workTree, String remote, String branch) {
        cli.run(workTree, switchArgs(branch, remote));
    }

    private static List<String> switchArgs(String branch, String remote) {
        return remote == null ? List.of("switch", "--no-guess", branch)
                : List.of("switch", "--create", branch, "--track", remote + "/" + branch);
    }

    @Override
    public StashedSwitch switchBranchWithStash(Path workTree, String branch, String remote, boolean includeUntracked,
                                               boolean restore) {
        if (branch.isEmpty() || branch.startsWith("-")) {
            throw new IllegalArgumentException("Not a branch: " + branch);
        }
        List<LoggedCommand> log = new ArrayList<>();
        String previous = readRepository(workTree, DefaultGitService::localBranch);
        ObjectId before = readRepository(workTree, DefaultGitService::stashId);
        List<String> stashArgs = new ArrayList<>(List.of("stash", "push"));
        if (includeUntracked) {
            stashArgs.add("--include-untracked");
        }
        stashArgs.add("--message=WinGit: automatic stash before switching to " + branch);
        if (!cli.runLogged(workTree, stashArgs, log).succeeded()) {
            return new StashedSwitch(StashedSwitch.Outcome.NOT_STASHED, List.of(), log);
        }
        ObjectId created = readRepository(workTree, DefaultGitService::stashId);
        boolean stashed = created != null && !created.equals(before);
        boolean switched = cli.runLogged(workTree, switchArgs(branch, remote), log).succeeded();
        if (!stashed) {
            return new StashedSwitch(switched ? StashedSwitch.Outcome.RESTORED : StashedSwitch.Outcome.NOT_SWITCHED,
                    List.of(), log);
        }
        if (switched && !restore) {
            return new StashedSwitch(StashedSwitch.Outcome.STASHED, List.of(), log, previous, created.name());
        }
        return popOwnStash(workTree, created, switched, previous, log);
    }

    @Override
    public Optional<List<SwitchClash>> predictSwitchClashes(Path workTree, String branch, String remote) {
        String target = (remote == null ? Constants.R_HEADS + branch : Constants.R_REMOTES + remote + "/" + branch)
                + "^{commit}";
        ObjectId head = readRepository(workTree, repository -> resolve(repository, Constants.HEAD + "^{commit}"));
        ObjectId other = readRepository(workTree, repository -> resolve(repository, target));
        if (head == null || other == null) {
            return Optional.empty();
        }
        Set<String> clashing = new LinkedHashSet<>();
        try {
            // A commit of the tracked changes, as a stash would hold them; stored nowhere.
            String changes = cli.run(workTree, List.of("stash", "create")).stdout().strip();
            if (!changes.isEmpty()) {
                GitResult merge = cli.runAllowing(workTree, List.of("merge-tree", "--write-tree", "--name-only",
                        "--no-messages", "-z", "--merge-base=" + head.name(), other.name(), changes), 1);
                if (merge.exitCode() == 1) {
                    String[] fields = merge.stdout().split("\0");
                    for (int i = 1; i < fields.length; i++) { // fields[0] is the merged tree
                        if (!fields[i].isEmpty()) {
                            clashing.add(fields[i]);
                        }
                    }
                }
            }
        } catch (GitCommandException e) {
            return Optional.empty(); // e.g. merge-tree without --write-tree or --merge-base (Git < 2.40)
        }
        List<String> untracked = status(workTree).changes().stream()
                .filter(c -> c.unstaged() == ChangeType.UNTRACKED).map(FileChange::path).toList();
        List<String> asked = new ArrayList<>(clashing);
        asked.addAll(untracked);
        Set<String> there = readRepository(workTree, repository -> existingIn(repository, other, asked));
        List<SwitchClash> result = new ArrayList<>();
        for (String path : clashing) {
            result.add(new SwitchClash(path, there.contains(path)
                    ? SwitchClash.Reason.CHANGED_THERE : SwitchClash.Reason.MISSING_THERE));
        }
        for (String path : untracked) {
            if (there.contains(path) && !clashing.contains(path)) {
                result.add(new SwitchClash(path, SwitchClash.Reason.EXISTS_THERE));
            }
        }
        return Optional.of(result);
    }

    private static ObjectId resolve(Repository repository, String revision) {
        try {
            return repository.resolve(revision);
        } catch (IOException e) {
            throw new GitException("Cannot read " + revision + ": " + e.getMessage(), e);
        }
    }

    /** Those of {@code paths} that are files in the tree of {@code commit}. */
    private static Set<String> existingIn(Repository repository, ObjectId commit, List<String> paths) {
        Set<String> existing = new LinkedHashSet<>();
        try (RevWalk walk = new RevWalk(repository)) {
            RevCommit parsed = walk.parseCommit(commit);
            for (String path : paths) {
                try (TreeWalk tree = TreeWalk.forPath(repository, path, parsed.getTree())) {
                    if (tree != null) {
                        existing.add(path);
                    }
                }
            }
        } catch (IOException e) {
            throw new GitException("Cannot read the branch: " + e.getMessage(), e);
        }
        return existing;
    }

    /** Applies and drops the stash just created, if it is still the newest one. */
    private StashedSwitch popOwnStash(Path workTree, ObjectId created, boolean switched, String previous,
                                      List<LoggedCommand> log) {
        StashedSwitch.Outcome kept = switched ? StashedSwitch.Outcome.STASH_KEPT
                : StashedSwitch.Outcome.NOT_SWITCHED_STASH_KEPT;
        if (!created.equals(readRepository(workTree, DefaultGitService::stashId))) {
            return new StashedSwitch(kept, List.of(), log); // someone else stashed meanwhile: do not guess
        }
        if (cli.runLogged(workTree, List.of("stash", "pop", "stash@{0}"), log).succeeded()) {
            return new StashedSwitch(switched ? StashedSwitch.Outcome.RESTORED : StashedSwitch.Outcome.NOT_SWITCHED,
                    List.of(), log);
        }
        List<String> conflicts = conflicts(workTree);
        return new StashedSwitch(switched && !conflicts.isEmpty() ? StashedSwitch.Outcome.CONFLICTS : kept,
                conflicts, log, previous, created.name());
    }

    @Override
    public StashedSwitch goBackFromStashedSwitch(Path workTree, StashedSwitch conflicted) {
        if (!conflicted.canGoBack()) {
            throw new IllegalArgumentException("This switch cannot be taken back: " + conflicted.outcome());
        }
        ObjectId stash = ObjectId.fromString(conflicted.stash());
        if (!stash.equals(readRepository(workTree, DefaultGitService::stashId))) {
            throw new GitException("The stash with the changes is no longer the newest one; nothing was changed.");
        }
        List<String> newFiles = unchangedNewFilesOf(workTree, stash);
        List<LoggedCommand> log = new ArrayList<>();
        List<List<String>> steps = new ArrayList<>();
        steps.add(List.of("reset", "--hard", "HEAD"));
        if (!newFiles.isEmpty()) {
            List<String> clean = new ArrayList<>(List.of("--literal-pathspecs", "clean", "--force", "--"));
            clean.addAll(newFiles);
            steps.add(clean);
        }
        steps.add(switchArgs(conflicted.previousBranch(), null));
        for (List<String> step : steps) {
            if (!cli.runLogged(workTree, step, log).succeeded()) {
                return new StashedSwitch(StashedSwitch.Outcome.NOT_SWITCHED_STASH_KEPT, List.of(), log);
            }
        }
        if (!stash.equals(readRepository(workTree, DefaultGitService::stashId))
                || !cli.runLogged(workTree, List.of("stash", "pop", "stash@{0}"), log).succeeded()) {
            return new StashedSwitch(StashedSwitch.Outcome.NOT_SWITCHED_STASH_KEPT, conflicts(workTree), log);
        }
        return new StashedSwitch(StashedSwitch.Outcome.NOT_SWITCHED, List.of(), log);
    }

    /**
     * The new (untracked) files saved in {@code stash} that are on disk with the same content, so they can
     * be removed; fails if one of them was changed since.
     */
    private List<String> unchangedNewFilesOf(Path workTree, ObjectId stash) {
        Map<String, String> saved = readRepository(workTree, repository -> untrackedOf(repository, stash));
        List<String> present = saved.keySet().stream().filter(p -> Files.exists(workTree.resolve(p))).toList();
        if (present.isEmpty()) {
            return present;
        }
        List<String> args = new ArrayList<>(List.of("hash-object", "--"));
        args.addAll(present);
        List<String> hashes = cli.run(workTree, args).stdout().lines().map(String::strip).toList();
        for (int i = 0; i < present.size(); i++) {
            if (i >= hashes.size() || !hashes.get(i).equals(saved.get(present.get(i)))) {
                throw new GitException("The new file " + present.get(i) + " was changed after the switch;"
                        + " nothing was changed.");
            }
        }
        return present;
    }

    /** Path → blob id of the untracked files a stash saved (its third parent), empty if none. */
    private static Map<String, String> untrackedOf(Repository repository, ObjectId stash) {
        Map<String, String> files = new LinkedHashMap<>();
        try (RevWalk walk = new RevWalk(repository)) {
            RevCommit commit = walk.parseCommit(stash);
            if (commit.getParentCount() < 3) {
                return files;
            }
            RevCommit untracked = walk.parseCommit(commit.getParent(2));
            try (TreeWalk tree = new TreeWalk(repository)) {
                tree.addTree(untracked.getTree());
                tree.setRecursive(true);
                while (tree.next()) {
                    files.put(tree.getPathString(), tree.getObjectId(0).name());
                }
            }
        } catch (IOException e) {
            throw new GitException("Cannot read the stash: " + e.getMessage(), e);
        }
        return files;
    }

    /** The checked-out local branch, {@code null} if HEAD is detached. */
    private static String localBranch(Repository repository) {
        try {
            String full = repository.getFullBranch();
            return full != null && full.startsWith(Constants.R_HEADS) ? Repository.shortenRefName(full) : null;
        } catch (IOException e) {
            throw new GitException("Cannot read the current branch: " + e.getMessage(), e);
        }
    }

    private List<String> conflicts(Path workTree) {
        return status(workTree).changes().stream()
                .filter(c -> c.staged() == ChangeType.CONFLICTED || c.unstaged() == ChangeType.CONFLICTED)
                .map(FileChange::path).toList();
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
            cli.run(parent, List.of("clone", "--progress", "--", url, absolute.toString()), progress,
                    sshEnvironment(sshConfiguredOutsideRepositories(), false), null);
        } catch (GitException e) {
            if (!existed) {
                deleteQuietly(absolute);
            }
            throw e;
        }
    }

    @Override
    public void stage(Path workTree, List<FileChange> changes) {
        List<String> paths = changes.stream().filter(c -> c.unstaged() != null).map(FileChange::path).toList();
        if (!paths.isEmpty()) {
            cli.run(workTree, List.of("--literal-pathspecs", "add", "--all",
                    "--pathspec-from-file=-", "--pathspec-file-nul"), null, Map.of(), nulSeparated(paths));
        }
    }

    @Override
    public void unstage(Path workTree, List<FileChange> changes) {
        // A rename is undone in the index only together with its original path.
        List<String> paths = changes.stream().filter(c -> c.staged() != null)
                .flatMap(c -> c.paths().stream()).toList();
        if (!paths.isEmpty()) {
            // 'reset' (unlike 'restore --staged') also works before the first commit.
            cli.run(workTree, List.of("--literal-pathspecs", "reset", "-q",
                    "--pathspec-from-file=-", "--pathspec-file-nul"), null, Map.of(), nulSeparated(paths));
        }
    }

    @Override
    public void discard(Path workTree, List<FileChange> changes) {
        List<String> untracked = new ArrayList<>();
        List<String> added = new ArrayList<>();
        List<String> tracked = new ArrayList<>();
        for (FileChange change : changes) {
            if (change.staged() == null && change.unstaged() == ChangeType.UNTRACKED) {
                untracked.add(change.path());
            } else if (change.staged() == ChangeType.ADDED) {
                added.add(change.path());
            } else if (change.staged() == ChangeType.RENAMED) {
                // Undo the move: drop the new path, bring the original back from HEAD.
                added.add(change.path());
                tracked.add(change.originalPath());
            } else {
                tracked.add(change.path());
            }
        }
        if (!untracked.isEmpty()) {
            List<String> args = new ArrayList<>(List.of("--literal-pathspecs", "clean", "-f", "-q", "--"));
            args.addAll(untracked);
            cli.run(workTree, args);
        }
        if (!added.isEmpty()) {
            // New in the index, not in HEAD: remove from index and disk.
            cli.run(workTree, List.of("--literal-pathspecs", "rm", "-f", "-q",
                    "--pathspec-from-file=-", "--pathspec-file-nul"), null, Map.of(), nulSeparated(added));
        }
        if (!tracked.isEmpty()) {
            cli.run(workTree, List.of("--literal-pathspecs", "restore", "--source=HEAD", "--staged", "--worktree",
                    "--pathspec-from-file=-", "--pathspec-file-nul"), null, Map.of(), nulSeparated(tracked));
        }
    }

    @Override
    public void commit(Path workTree, List<FileChange> changes, String message, Instant date, boolean amend) {
        if (changes.isEmpty() && !amend) {
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
            paths.addAll(change.paths()); // a rename is committed together with the deletion of its original
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
        // An amend takes the author (with its date) from the old commit; only --date replaces that date.
        List<String> amendArgs = new ArrayList<>();
        if (amend) {
            amendArgs.add("--amend");
            if (date != null) {
                amendArgs.add("--date=" + gitDate(date, ZoneId.systemDefault()));
            }
        }
        if (readRepository(workTree, r -> r.getRepositoryState() != RepositoryState.SAFE)) {
            if (amend) {
                throw new IllegalStateException("Cannot amend while a merge or similar operation is in progress");
            }
            cli.run(workTree, List.of("commit", "--file=-"), null, environment, message);
            return;
        }
        if (paths.isEmpty()) {
            // only the message of the last commit: --only without paths leaves other staged changes out
            List<String> args = new ArrayList<>(List.of("commit", "--only", "--file=-"));
            args.addAll(amendArgs);
            cli.run(workTree, args, null, environment, message);
            return;
        }
        Path pathspecFile = null;
        try {
            pathspecFile = Files.createTempFile("wingit-commit-", ".paths");
            Files.writeString(pathspecFile, nulSeparated(paths), StandardCharsets.UTF_8);
            List<String> args = new ArrayList<>(List.of("--literal-pathspecs", "commit", "--file=-",
                    "--pathspec-from-file=" + pathspecFile, "--pathspec-file-nul"));
            args.addAll(amendArgs);
            cli.run(workTree, args, null, environment, message);
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

    /** Local config key ({@code wingit.commitDate}) of the remembered commit date, an ISO-8601 instant. */
    private static final String WINGIT_SECTION = "wingit";
    private static final String COMMIT_DATE_KEY = "commitDate";

    @Override
    public Optional<Instant> savedCommitDate(Path workTree) {
        String value;
        try {
            value = readRepository(workTree,
                    r -> r.getConfig().getString(WINGIT_SECTION, null, COMMIT_DATE_KEY));
        } catch (GitException e) {
            return Optional.empty();
        }
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(Instant.parse(value.strip()));
        } catch (DateTimeParseException e) {
            return Optional.empty();
        }
    }

    @Override
    public void setSavedCommitDate(Path workTree, Instant date) {
        String key = WINGIT_SECTION + "." + COMMIT_DATE_KEY;
        if (date != null) {
            cli.run(workTree, List.of("config", "--local", key, date.toString()));
        } else if (readRepository(workTree,
                r -> r.getConfig().getString(WINGIT_SECTION, null, COMMIT_DATE_KEY) != null)) {
            // 'git config --unset' fails when the key is missing, hence the check
            cli.run(workTree, List.of("config", "--local", "--unset-all", key));
        }
    }

    @Override
    public AmendCheck amendCheck(Path workTree) {
        return readRepository(workTree, r -> {
            try {
                ObjectId head = r.resolve(Constants.HEAD);
                if (head == null) {
                    return AmendCheck.notPossible(AmendCheck.State.NO_COMMIT);
                }
                if (r.getRepositoryState() != RepositoryState.SAFE) {
                    return AmendCheck.notPossible(AmendCheck.State.OPERATION_IN_PROGRESS);
                }
                if (LogReader.isOnRemoteBranch(r, head)) {
                    return AmendCheck.notPossible(AmendCheck.State.PUSHED);
                }
                try (RevWalk walk = new RevWalk(r)) {
                    return new AmendCheck(AmendCheck.State.POSSIBLE, walk.parseCommit(head).getFullMessage());
                }
            } catch (IOException e) {
                throw new GitException("Cannot read history of " + workTree + ": " + e.getMessage(), e);
            }
        });
    }

    /** File in the Git directory with the message of a commit not made yet. */
    static final String COMMIT_DRAFT_FILE = "WINGIT_COMMIT_MSG";

    @Override
    public Optional<String> commitDraft(Path workTree) {
        try {
            Path file = readRepository(workTree, r -> r.getDirectory().toPath()).resolve(COMMIT_DRAFT_FILE);
            if (!Files.isRegularFile(file)) {
                return Optional.empty();
            }
            String text = Files.readString(file, StandardCharsets.UTF_8);
            return text.isBlank() ? Optional.empty() : Optional.of(text);
        } catch (GitException | IOException e) {
            return Optional.empty();
        }
    }

    @Override
    public void setCommitDraft(Path workTree, String message) {
        Path file = readRepository(workTree, r -> r.getDirectory().toPath()).resolve(COMMIT_DRAFT_FILE);
        try {
            if (message == null || message.isBlank()) {
                Files.deleteIfExists(file);
            } else {
                Files.writeString(file, message, StandardCharsets.UTF_8);
            }
        } catch (IOException e) {
            throw new GitException("Cannot save the commit message draft: " + e.getMessage(), e);
        }
    }

    @Override
    public void push(Path workTree, Credentials credentials, Consumer<String> progress) {
        RemoteTarget target = readRepository(workTree, RemoteTarget::forPush);
        String source = "refs/heads/" + target.branch();
        List<String> args = new ArrayList<>(List.of("push", "--progress", "--follow-tags"));
        if (target.hasUpstream()) {
            args.addAll(List.of(target.remote(), source + ":" + target.remoteRef()));
        } else {
            args.addAll(List.of("--set-upstream", target.remote(), source + ":" + source));
        }
        runRemote(workTree, args, target.url(), credentials, progress);
    }

    @Override
    public void fetch(Path workTree, Credentials credentials, Consumer<String> progress) {
        FetchSource source = readRepository(workTree, FetchSource::read);
        if (source == null) {
            throw new GitException("The repository has no remote to fetch from.");
        }
        runRemote(workTree, source.args(true), source.url(), credentials, progress);
    }

    @Override
    public boolean fetchInBackground(Path workTree) {
        FetchSource source = readRepository(workTree, FetchSource::read);
        if (source == null) {
            return false;
        }
        Map<String, String> environment = new HashMap<>(AskPass.storedOnlyEnvironment());
        // OpenSSH would wait for a passphrase or a host key answer nobody sees; fail instead.
        environment.putAll(sshEnvironment(!source.defaultSsh(), true));
        // Git reports updated refs on stderr; nothing there means nothing changed.
        return !cli.run(workTree, source.args(false), null, environment, null).stderr().isBlank();
    }

    /**
     * What {@code git fetch} is run with.
     *
     * @param remote     the remote to fetch, {@code null} for all remotes
     * @param url        its URL (of some remote for all), for sign-in
     * @param defaultSsh SSH is not configured by the user ({@code core.sshCommand}, {@code GIT_SSH...}),
     *                   so Git uses plain {@code ssh}
     */
    private record FetchSource(String remote, String url, boolean defaultSsh) {

        /** {@code null} for a repository without remotes. */
        static FetchSource read(Repository repository) {
            StoredConfig config = repository.getConfig();
            Set<String> remotes = config.getSubsections(ConfigConstants.CONFIG_REMOTE_SECTION);
            if (remotes.isEmpty()) {
                return null;
            }
            String remote = RemoteTarget.defaultRemote(repository).orElse(null);
            String url = config.getString(ConfigConstants.CONFIG_REMOTE_SECTION,
                    remote != null ? remote : remotes.iterator().next(), ConfigConstants.CONFIG_KEY_URL);
            boolean defaultSsh = !sshConfigured(config);
            return new FetchSource(remote, url != null ? url : remote, defaultSsh);
        }

        List<String> args(boolean progress) {
            List<String> args = new ArrayList<>(List.of("fetch", "--prune"));
            if (progress) {
                args.add("--progress");
            }
            args.add(remote != null ? remote : "--all");
            return args;
        }
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
    public void deleteRemoteBranch(Path workTree, String remote, String branch, Credentials credentials,
                                   Consumer<String> progress) {
        String url = readRepository(workTree, repository -> RemoteTarget.pushUrl(repository, remote));
        runRemote(workTree, List.of("push", "--progress", remote, "--delete", "refs/heads/" + branch),
                url, credentials, progress);
    }

    @Override
    public void renameRemoteBranch(Path workTree, String remote, String name, String newName,
                                   Credentials credentials, Consumer<String> progress) {
        if (!BranchNames.isValid(newName)) {
            throw new IllegalArgumentException("Not a valid branch name: " + newName);
        }
        String[] urlAndId = readRepository(workTree, repository -> {
            try {
                ObjectId id = repository.resolve(Constants.R_REMOTES + remote + "/" + name);
                if (id == null) {
                    throw new GitException("Remote branch '" + remote + "/" + name + "' is not known; fetch first.");
                }
                return new String[] {RemoteTarget.pushUrl(repository, remote), id.name()};
            } catch (IOException e) {
                throw new GitException("Cannot read " + remote + "/" + name + ": " + e.getMessage(), e);
            }
        });
        String oldRef = Constants.R_HEADS + name;
        String newRef = Constants.R_HEADS + newName;
        // The leases make the push fail (atomically, nothing changes) if the old branch moved on the
        // server since the last fetch or the new name is already taken there.
        runRemote(workTree, List.of("push", "--progress", "--atomic",
                "--force-with-lease=" + oldRef + ":" + urlAndId[1],
                "--force-with-lease=" + newRef + ":",
                remote, urlAndId[1] + ":" + newRef, ":" + oldRef), urlAndId[0], credentials, progress);
        retrack(workTree, remote, oldRef, newRef);
    }

    /** Points local branches tracking {@code remote}/{@code oldRef} at {@code newRef}. */
    private void retrack(Path workTree, String remote, String oldRef, String newRef) {
        List<String> tracking = readRepository(workTree, repository -> {
            StoredConfig config = repository.getConfig();
            List<String> result = new ArrayList<>();
            for (String branch : config.getSubsections(ConfigConstants.CONFIG_BRANCH_SECTION)) {
                if (remote.equals(config.getString(ConfigConstants.CONFIG_BRANCH_SECTION, branch,
                        ConfigConstants.CONFIG_KEY_REMOTE))
                        && oldRef.equals(config.getString(ConfigConstants.CONFIG_BRANCH_SECTION, branch,
                        ConfigConstants.CONFIG_KEY_MERGE))) {
                    result.add(branch);
                }
            }
            return result;
        });
        for (String branch : tracking) {
            cli.run(workTree, List.of("config", "--local", "branch." + branch + ".merge", newRef));
        }
    }

    @Override
    public void storeCredentials(Path workTree, String url, Credentials credentials) {
        String input = "url=" + singleLine(url) + "\n"
                + "username=" + singleLine(credentials.username()) + "\n"
                + "password=" + singleLine(credentials.password()) + "\n\n";
        // The user name first: it also helps when the helper already holds the password but has
        // several accounts for this host and cannot choose one without asking.
        String origin = credentialOrigin(url);
        if (origin != null) {
            cli.run(workTree, List.of("config", "--local", "credential." + origin + ".username",
                    credentials.username()));
        }
        cli.run(workTree, List.of("credential", "approve"), null, Map.of(), input);
    }

    /** {@code https://host[:port]} of an HTTP(S) URL (without user and path), or {@code null}. */
    static String credentialOrigin(String url) {
        try {
            URI uri = new URI(url);
            String scheme = uri.getScheme();
            if (uri.getHost() == null || scheme == null
                    || !(scheme.equalsIgnoreCase("https") || scheme.equalsIgnoreCase("http"))) {
                return null;
            }
            return scheme.toLowerCase(Locale.ROOT) + "://" + uri.getHost()
                    + (uri.getPort() >= 0 ? ":" + uri.getPort() : "");
        } catch (URISyntaxException e) {
            return null;
        }
    }

    private GitResult runRemote(Path workTree, List<String> args, String url, Credentials credentials,
                                Consumer<String> progress) {
        List<String> command = credentials == null ? args : AskPass.withOptions(args);
        Map<String, String> environment = new HashMap<>(credentials == null
                ? AskPass.storedOnlyEnvironment() : AskPass.environment(credentials));
        environment.putAll(sshEnvironment(readRepository(workTree, r -> sshConfigured(r.getConfig())), false));
        try {
            return cli.run(workTree, command, progress, environment, null);
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
