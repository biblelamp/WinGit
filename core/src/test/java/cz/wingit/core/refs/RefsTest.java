package cz.wingit.core.refs;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.PersonIdent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import cz.wingit.core.DefaultGitService;
import cz.wingit.core.GitService;
import cz.wingit.core.cli.GitCli;

class RefsTest {

    private static final PersonIdent IDENT = new PersonIdent("Test", "test@example.invalid");

    @TempDir
    Path tempDir;

    private final GitService git = new DefaultGitService(new GitCli());

    @Test
    void readsLocalRemoteTagsAndStashes() throws Exception {
        Path server = tempDir.resolve("server");
        try (Git origin = Git.init().setDirectory(server.toFile()).setInitialBranch("main").call()) {
            commit(origin, server, "a.txt", "1");
            origin.branchCreate().setName("release/1.0").call();
        }

        Path clone = tempDir.resolve("clone");
        try (Git local = Git.cloneRepository().setURI(server.toUri().toString())
                .setDirectory(clone.toFile()).call()) {
            commit(local, clone, "a.txt", "local");             // main is 1 ahead of origin/main
            local.branchCreate().setName("Feature").call();     // no upstream
            local.tag().setName("v1.0").setTagger(IDENT).setMessage("v1.0").setSigned(false).call();
            Files.writeString(clone.resolve("a.txt"), "wip", StandardCharsets.UTF_8);
            local.stashCreate().setPerson(IDENT).setWorkingDirectoryMessage("work in progress").call();
        }
        try (Git origin = Git.open(server.toFile())) {
            commit(origin, server, "b.txt", "server");          // after fetch, main is also 1 behind
        }
        try (Git local = Git.open(clone.toFile())) {
            local.fetch().call();
        }

        RepositoryRefs refs = git.refs(clone);

        assertThat(refs.localBranches()).containsExactly(
                new LocalBranch("Feature", null, 0, 0, false),
                new LocalBranch("main", "origin/main", 1, 1, true));
        assertThat(refs.detachedAt()).isNull();
        assertThat(refs.remotes()).containsExactly(
                new RemoteBranches("origin", List.of("main", "release/1.0"), server.toUri().toString()));
        assertThat(refs.tags()).containsExactly("v1.0");
        assertThat(refs.stashes()).hasSize(1);
        Stash stash = refs.stashes().get(0);
        assertThat(stash.index()).isZero();
        assertThat(stash.ref()).isEqualTo("stash@{0}");
        assertThat(stash.message()).isEqualTo("work in progress");
        assertThat(stash.id()).matches("[0-9a-f]{40}");
    }

    @Test
    void tagsCarryTheSubjectOfTheirCommit() throws Exception {
        Path repo = tempDir.resolve("tags");
        try (Git jgit = Git.init().setDirectory(repo.toFile()).setInitialBranch("main").call()) {
            commit(jgit, repo, "a.txt", "first release\n\nwith details");
            jgit.tag().setName("v1").setTagger(IDENT).setMessage("annotation, not shown").setSigned(false).call();
            commit(jgit, repo, "a.txt", "small fixes");
            jgit.tag().setName("light").setAnnotated(false).call();
            jgit.tag().setName("tree").setObjectId(new org.eclipse.jgit.revwalk.RevWalk(jgit.getRepository())
                    .parseTree(jgit.getRepository().resolve("HEAD^{tree}"))).setAnnotated(false).call();

            RepositoryRefs refs = git.refs(repo);

            assertThat(refs.tags()).containsExactly("light", "tree", "v1");
            assertThat(refs.tagSubjects()).containsOnly(
                    Map.entry("v1", "first release"), Map.entry("light", "small fixes"));
        }
    }

    @Test
    void reportsDetachedHead() throws Exception {
        Path repo = tempDir.resolve("detached");
        try (Git jgit = Git.init().setDirectory(repo.toFile()).setInitialBranch("main").call()) {
            commit(jgit, repo, "a.txt", "1");
            String id = jgit.getRepository().resolve("HEAD").name();
            jgit.checkout().setName(id).call();

            RepositoryRefs refs = git.refs(repo);

            assertThat(refs.detachedAt()).isEqualTo(id.substring(0, 7));
            assertThat(refs.localBranches()).extracting(LocalBranch::current).containsOnly(false);
            assertThat(refs.remotes()).isEmpty();
        }
    }

    private static void commit(Git jgit, Path dir, String file, String content) throws Exception {
        Files.writeString(dir.resolve(file), content, StandardCharsets.UTF_8);
        jgit.add().addFilepattern(file).call();
        jgit.commit().setMessage(content).setAuthor(IDENT).setCommitter(IDENT).setSign(false).call();
    }
}
