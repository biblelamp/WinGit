package cz.wingit.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.StoredConfig;
import org.eclipse.jgit.revwalk.RevTag;
import org.eclipse.jgit.revwalk.RevWalk;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import cz.wingit.core.cli.GitCli;

class TagTest {

    @TempDir
    Path tempDir;

    private final GitCli cli = new GitCli();
    private final GitService git = new DefaultGitService(cli);

    @Test
    void createsAnnotatedTagOnHeadWithMessage() throws Exception {
        Path repo = createRepo("head");
        String head = commit(repo, "a.txt", "first");

        git.createTag(repo, "v1.0.0", null, "  Release 1.0\n\nŽluťoučký kůň  ");

        RevTag tag = tag(repo, "v1.0.0");
        assertThat(tag.getObject().name()).isEqualTo(head);
        assertThat(tag.getFullMessage().strip()).isEqualTo("Release 1.0\n\nŽluťoučký kůň");
        assertThat(tag.getTaggerIdent().getName()).isEqualTo("Test");
        assertThat(git.refs(repo).tags()).containsExactly("v1.0.0");
    }

    @Test
    void createsTagOnOlderCommitWithNameAsDefaultMessage() throws Exception {
        Path repo = createRepo("older");
        String first = commit(repo, "a.txt", "first");
        commit(repo, "b.txt", "second");

        git.createTag(repo, "old", first.substring(0, 7), "");

        RevTag tag = tag(repo, "old");
        assertThat(tag.getObject().name()).isEqualTo(first);
        assertThat(tag.getFullMessage().strip()).isEqualTo("old");
    }

    @Test
    void refusesInvalidNamesExistingTagsAndOptionLikeCommits() throws Exception {
        Path repo = createRepo("invalid");
        commit(repo, "a.txt", "first");
        git.createTag(repo, "v1", null, null);

        assertThatThrownBy(() -> git.createTag(repo, "bad name", null, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> git.createTag(repo, "-f", null, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> git.createTag(repo, "v2", "--force", null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> git.createTag(repo, "v1", null, null))
                .isInstanceOf(GitCommandException.class);
    }

    @Test
    void deletesLocalTag() throws Exception {
        Path repo = createRepo("delete");
        commit(repo, "a.txt", "first");
        git.createTag(repo, "v1", null, null);
        git.createTag(repo, "v2", null, null);

        git.deleteTag(repo, "v1");

        assertThat(git.refs(repo).tags()).containsExactly("v2");
    }

    @Test
    void pushTakesNewAnnotatedTagsAlongAndTagsCanBePushedAndDeletedOnServer() throws Exception {
        Path remote = tempDir.resolve("server.git");
        cli.run(tempDir, List.of("init", "--bare", "-q", remote.toString()));
        Path repo = createRepo("local");
        cli.run(repo, List.of("remote", "add", "origin", remote.toString()));
        commit(repo, "a.txt", "first");
        git.createTag(repo, "v0.1.0", null, "First");
        assertThat(git.defaultRemote(repo)).contains("origin");

        git.push(repo, null, line -> { });
        assertThat(serverTags(remote)).containsExactly("v0.1.0");

        // a tag on an already pushed commit, created later, can be pushed on its own
        git.createTag(repo, "later", null, null);
        git.pushTag(repo, "origin", "later", null, line -> { });
        assertThat(serverTags(remote)).containsExactlyInAnyOrder("v0.1.0", "later");

        git.deleteRemoteTag(repo, "origin", "later", null, line -> { });
        assertThat(serverTags(remote)).containsExactly("v0.1.0");
        assertThat(git.refs(repo).tags()).contains("later"); // local tag stays
    }

    @Test
    void defaultRemoteIsEmptyWithoutRemotes() throws Exception {
        Path repo = createRepo("noremote");
        commit(repo, "a.txt", "first");

        assertThat(git.defaultRemote(repo)).isEmpty();

        cli.run(repo, List.of("remote", "add", "upstream", "https://example.invalid/r.git"));
        assertThat(git.defaultRemote(repo)).contains("upstream");
        cli.run(repo, List.of("remote", "add", "fork", "https://example.invalid/f.git"));
        assertThat(git.defaultRemote(repo)).isEmpty(); // two remotes, none obvious
    }

    @Test
    void renameKeepsAnnotatedTagContentAndRenamingBackRestoresIt() throws Exception {
        Path repo = createRepo("rename");
        String first = commit(repo, "a.txt", "first");
        commit(repo, "b.txt", "second");
        // a tag by someone else, made earlier, on an older commit
        cli.run(repo, List.of("-c", "user.name=Alice", "-c", "user.email=alice@example.invalid", "tag", "-a",
                "-m", "Release one\n\nNotes", "v1", first), null,
                Map.of("GIT_COMMITTER_DATE", "1700000000 +0130"), null);
        String original = tagId(repo, "v1");

        git.renameTag(repo, "v1", "release-1");

        assertThat(git.refs(repo).tags()).containsExactly("release-1");
        RevTag renamed = tag(repo, "release-1");
        assertThat(renamed.getTagName()).isEqualTo("release-1");
        assertThat(renamed.getObject().name()).isEqualTo(first);
        assertThat(renamed.getFullMessage()).isEqualTo("Release one\n\nNotes\n");
        assertThat(renamed.getTaggerIdent().getName()).isEqualTo("Alice");
        assertThat(renamed.getTaggerIdent().getWhenAsInstant().getEpochSecond()).isEqualTo(1700000000L);

        git.renameTag(repo, "release-1", "v1");
        assertThat(tagId(repo, "v1")).isEqualTo(original); // identical tag object again
    }

    @Test
    void renameKeepsLightweightTagsLightweightAndAllowsCaseChange() throws Exception {
        Path repo = createRepo("light");
        String head = commit(repo, "a.txt", "first");
        cli.run(repo, List.of("tag", "light"));

        git.renameTag(repo, "light", "Light");

        assertThat(git.refs(repo).tags()).containsExactly("Light");
        assertThat(tagId(repo, "Light")).isEqualTo(head); // still points straight at the commit
    }

    @Test
    void renameToExistingNameFailsAndKeepsTheTag() throws Exception {
        Path repo = createRepo("taken");
        commit(repo, "a.txt", "first");
        git.createTag(repo, "a", null, null);
        git.createTag(repo, "b", null, null);

        assertThatThrownBy(() -> git.renameTag(repo, "a", "b")).isInstanceOf(GitCommandException.class);
        assertThatThrownBy(() -> git.renameTag(repo, "a", "bad name")).isInstanceOf(IllegalArgumentException.class);
        assertThat(git.refs(repo).tags()).containsExactly("a", "b");
    }

    @Test
    void renameRemoteTagReplacesItOnServerAndRefusesTakenNames() throws Exception {
        Path remote = tempDir.resolve("rename-server.git");
        cli.run(tempDir, List.of("init", "--bare", "-q", remote.toString()));
        Path repo = createRepo("rename-local");
        cli.run(repo, List.of("remote", "add", "origin", remote.toString()));
        commit(repo, "a.txt", "first");
        git.createTag(repo, "old", null, null);
        git.createTag(repo, "taken", null, null);
        git.createTag(repo, "local-only", null, null);
        git.push(repo, null, line -> { });
        cli.run(repo, List.of("push", "-q", "origin", "refs/tags/old", "refs/tags/taken"));
        cli.run(repo, List.of("push", "-q", "origin", ":refs/tags/local-only"));
        assertThat(serverTags(remote)).containsExactlyInAnyOrder("old", "taken");

        git.renameTag(repo, "old", "new");
        git.renameRemoteTag(repo, "origin", "old", "new", null, line -> { });
        assertThat(serverTags(remote)).containsExactlyInAnyOrder("new", "taken");

        // not on the server yet: the new name is simply pushed
        git.renameTag(repo, "local-only", "pushed");
        git.renameRemoteTag(repo, "origin", "local-only", "pushed", null, line -> { });
        assertThat(serverTags(remote)).containsExactlyInAnyOrder("new", "taken", "pushed");

        // the new name exists on the server: nothing changes there
        cli.run(repo, List.of("tag", "--delete", "taken"));
        git.renameTag(repo, "new", "taken");
        assertThatThrownBy(() -> git.renameRemoteTag(repo, "origin", "new", "taken", null, line -> { }))
                .isInstanceOf(GitCommandException.class);
        assertThat(serverTags(remote)).containsExactlyInAnyOrder("new", "taken", "pushed");
    }

    private static String tagId(Path repo, String name) throws Exception {
        try (Git jgit = Git.open(repo.toFile())) {
            return jgit.getRepository().exactRef("refs/tags/" + name).getObjectId().name();
        }
    }

    private List<String> serverTags(Path remote) throws Exception {
        try (Git jgit = Git.open(remote.toFile())) {
            return jgit.tagList().call().stream().map(Ref::getName).map(Repository::shortenRefName).toList();
        }
    }

    private static RevTag tag(Path repo, String name) throws Exception {
        try (Git jgit = Git.open(repo.toFile()); RevWalk walk = new RevWalk(jgit.getRepository())) {
            Ref ref = jgit.getRepository().exactRef("refs/tags/" + name);
            return walk.parseTag(ref.getObjectId());
        }
    }

    private String commit(Path repo, String file, String message) throws Exception {
        Files.writeString(repo.resolve(file), message, StandardCharsets.UTF_8);
        git.commit(repo, git.status(repo).changes(), message);
        try (Git jgit = Git.open(repo.toFile())) {
            return jgit.getRepository().resolve("HEAD").name();
        }
    }

    private Path createRepo(String name) throws Exception {
        Path dir = Files.createDirectory(tempDir.resolve(name));
        git.init(dir);
        try (Git jgit = Git.open(dir.toFile())) {
            StoredConfig config = jgit.getRepository().getConfig();
            config.setString("user", null, "name", "Test");
            config.setString("user", null, "email", "test@example.invalid");
            config.setBoolean("commit", null, "gpgsign", false);
            config.setBoolean("tag", null, "gpgsign", false);
            config.setString("core", null, "hooksPath", "no-hooks");
            config.save();
        }
        return dir;
    }
}
