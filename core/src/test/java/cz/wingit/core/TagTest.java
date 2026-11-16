package cz.wingit.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

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
