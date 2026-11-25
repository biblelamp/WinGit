package cz.wingit.core.log;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.TimeZone;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.RefUpdate;
import org.eclipse.jgit.lib.StoredConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import cz.wingit.core.DefaultGitService;
import cz.wingit.core.GitException;
import cz.wingit.core.GitService;
import cz.wingit.core.cli.GitCli;

class HistoryReaderTest {

    private static final Instant BASE = Instant.parse("2026-01-01T10:00:00Z");

    @TempDir
    Path repo;

    private final GitService git = new DefaultGitService(new GitCli());
    private int time;

    @Test
    void readsTheWholeHistoryInPortionsNewestFirst() throws Exception {
        try (Git jgit = init()) {
            for (int i = 1; i <= 7; i++) {
                commit(jgit, "c" + i);
            }
        }

        List<String> messages = new ArrayList<>();
        try (HistoryReader reader = git.openHistory(repo, "HEAD")) {
            assertThat(reader.next(3)).extracting(CommitInfo::message).containsExactly("c7", "c6", "c5");
            assertThat(reader.count()).isEqualTo(3);
            List<CommitInfo> portion;
            while (!(portion = reader.next(3)).isEmpty()) {
                portion.forEach(c -> messages.add(c.message()));
            }
            assertThat(reader.count()).isEqualTo(7);
            assertThat(reader.next(3)).isEmpty();
        }
        assertThat(messages).containsExactly("c4", "c3", "c2", "c1");
    }

    @Test
    void historyOfAnotherBranchOrTagStartsThere() throws Exception {
        try (Git jgit = init()) {
            commit(jgit, "base");
            jgit.branchCreate().setName("feature").call();
            jgit.tag().setName("v1").setAnnotated(true).setMessage("v1").setSigned(false).call();
            commit(jgit, "on main");
            jgit.checkout().setName("feature").call();
            commit(jgit, "on feature");
            jgit.checkout().setName("main").call();
        }

        assertThat(read("refs/heads/feature")).containsExactly("on feature", "base");
        assertThat(read("refs/tags/v1")).containsExactly("base");
        assertThat(read("HEAD")).containsExactly("on main", "base");
    }

    @Test
    void marksUnpushedAndIncludesUnpulledCommitsOfTheUpstream() throws Exception {
        try (Git jgit = init()) {
            String base = commit(jgit, "base");
            String local = commit(jgit, "local");
            // origin/main: base + one commit the local branch does not have yet
            jgit.checkout().setName("tmp").setCreateBranch(true).setStartPoint(base).call();
            String remote = commit(jgit, "remote");
            jgit.checkout().setName("main").call();
            jgit.branchDelete().setBranchNames("tmp").setForce(true).call();
            setRef(jgit, "refs/remotes/origin/main", remote);
            StoredConfig config = jgit.getRepository().getConfig();
            config.setString("branch", "main", "remote", "origin");
            config.setString("branch", "main", "merge", "refs/heads/main");
            config.setString("remote", "origin", "fetch", "+refs/heads/*:refs/remotes/origin/*");
            config.save();
            assertThat(local).isNotEqualTo(remote);
        }

        List<CommitInfo> all = readAll("HEAD");
        assertThat(all).extracting(CommitInfo::message).containsExactly("remote", "local", "base");
        assertThat(all).extracting(CommitInfo::unpulled).containsExactly(true, false, false);
        assertThat(all).extracting(CommitInfo::unpushed).containsExactly(false, true, false);

        // the remote branch itself: nothing to pull, nothing unpushed
        List<CommitInfo> remote = readAll("refs/remotes/origin/main");
        assertThat(remote).extracting(CommitInfo::message).containsExactly("remote", "base");
        assertThat(remote).noneMatch(c -> c.unpulled() || c.unpushed());
    }

    @Test
    void unpushedMarksAreNotLimitedToTheFirstPortion() throws Exception {
        String first;
        try (Git jgit = init()) {
            first = commit(jgit, "pushed");
            for (int i = 0; i < 5; i++) {
                commit(jgit, "local " + i);
            }
            setRef(jgit, "refs/remotes/origin/main", first);
        }

        try (HistoryReader reader = git.openHistory(repo, "HEAD")) {
            assertThat(reader.next(2)).allMatch(CommitInfo::unpushed);
            assertThat(reader.next(10)).extracting(CommitInfo::unpushed).containsExactly(true, true, true, false);
        }
    }

    @Test
    void emptyRepositoryHasEmptyHistoryAndUnknownRevisionIsAnError() throws Exception {
        init().close();

        try (HistoryReader reader = git.openHistory(repo, "HEAD")) {
            assertThat(reader.next(10)).isEmpty();
        }
        assertThatThrownBy(() -> git.openHistory(repo, "refs/heads/missing")).isInstanceOf(GitException.class);
    }

    private Git init() throws Exception {
        return Git.init().setDirectory(repo.toFile()).setInitialBranch("main").call();
    }

    /** Commits with increasing times, so the order by commit time is the order of creation. */
    private String commit(Git jgit, String message) throws Exception {
        Files.writeString(repo.resolve("a.txt"), message, StandardCharsets.UTF_8);
        jgit.add().addFilepattern("a.txt").call();
        PersonIdent ident = new PersonIdent(new PersonIdent("Test", "test@example.invalid"),
                Date.from(BASE.plusSeconds(60L * ++time)), TimeZone.getTimeZone(ZoneOffset.UTC));
        return jgit.commit().setMessage(message).setAuthor(ident).setCommitter(ident).setSign(false).call()
                .getName();
    }

    private static void setRef(Git jgit, String name, String id) throws Exception {
        RefUpdate update = jgit.getRepository().updateRef(name);
        update.setNewObjectId(ObjectId.fromString(id));
        update.forceUpdate();
    }

    private List<String> read(String revision) {
        return readAll(revision).stream().map(CommitInfo::message).toList();
    }

    private List<CommitInfo> readAll(String revision) {
        List<CommitInfo> all = new ArrayList<>();
        try (HistoryReader reader = git.openHistory(repo, revision)) {
            List<CommitInfo> portion;
            while (!(portion = reader.next(2)).isEmpty()) {
                all.addAll(portion);
            }
        }
        return all;
    }
}
