package cz.wingit.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.StoredConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import cz.wingit.core.cli.GitCli;
import cz.wingit.core.cli.LoggedCommand;
import cz.wingit.core.refs.StashedSwitch;
import cz.wingit.core.refs.StashedSwitch.Outcome;
import cz.wingit.core.refs.SwitchClash;

class StashedSwitchTest {

    private static final String BASE = "1\n2\n3\n4\n5\n6\n7\n8\n";

    @TempDir
    Path repo;

    private final GitService git = new DefaultGitService(new GitCli());
    private String main;

    @Test
    void carriesNonClashingChangesToTheOtherBranch() throws Exception {
        init();
        branchChangingFirstLine();
        write("a.txt", BASE.replace("8\n", "8 local\n"));
        write("new.txt", "new\n");
        assertThatThrownBy(() -> git.switchBranch(repo, "other")).isInstanceOf(GitCommandException.class);

        StashedSwitch result = git.switchBranchWithStash(repo, "other", null, true, true);

        assertThat(result.outcome()).isEqualTo(Outcome.RESTORED);
        assertThat(result.switched()).isTrue();
        assertThat(git.currentBranch(repo)).hasValue("other");
        assertThat(repo.resolve("a.txt")).hasContent("1 other\n" + BASE.substring(2).replace("8\n", "8 local\n"));
        assertThat(repo.resolve("new.txt")).hasContent("new\n");
        assertThat(git.refs(repo).stashes()).isEmpty();
        assertThat(result.log()).extracting(LoggedCommand::command).containsExactly(
                "git stash push --include-untracked --message=WinGit: automatic stash before switching to other",
                "git switch --no-guess other",
                "git stash pop stash@{0}");
        assertThat(result.log()).allMatch(LoggedCommand::succeeded);
    }

    @Test
    void clashingChangesEndInConflictsAndTheStashIsKept() throws Exception {
        init();
        branchChangingFirstLine();
        write("a.txt", BASE.replace("1\n", "1 local\n"));

        StashedSwitch result = git.switchBranchWithStash(repo, "other", null, false, true);

        assertThat(result.outcome()).isEqualTo(Outcome.CONFLICTS);
        assertThat(result.conflicts()).containsExactly("a.txt");
        assertThat(git.currentBranch(repo)).hasValue("other");
        assertThat(git.refs(repo).stashes()).hasSize(1);
        assertThat(result.log().get(2).succeeded()).isFalse();
    }

    @Test
    void failedSwitchBringsTheChangesBackWhereTheyWere() throws Exception {
        init();
        write("a.txt", "local\n");

        StashedSwitch result = git.switchBranchWithStash(repo, "no-such-branch", null, true, true);

        assertThat(result.outcome()).isEqualTo(Outcome.NOT_SWITCHED);
        assertThat(result.switched()).isFalse();
        assertThat(git.currentBranch(repo)).hasValue(main);
        assertThat(repo.resolve("a.txt")).hasContent("local\n");
        assertThat(git.refs(repo).stashes()).isEmpty();
        assertThat(result.log().get(1).succeeded()).isFalse();
        assertThat(result.log().get(1).output()).isNotBlank();
    }

    @Test
    void conflictedSwitchCanBeTakenBack() throws Exception {
        StashedSwitch conflicted = switchAwayFromFileOnlyOnFeature();
        assertThat(conflicted.outcome()).isEqualTo(Outcome.CONFLICTS);
        assertThat(conflicted.canGoBack()).isTrue();
        assertThat(conflicted.previousBranch()).isEqualTo("feature");

        StashedSwitch back = git.goBackFromStashedSwitch(repo, conflicted);

        assertThat(back.outcome()).isEqualTo(Outcome.NOT_SWITCHED);
        assertThat(back.log()).allMatch(LoggedCommand::succeeded);
        assertThat(git.currentBranch(repo)).hasValue("feature");
        assertThat(repo.resolve("f.md")).hasContent("feature local\n");
        assertThat(repo.resolve("new.txt")).hasContent("new\n");
        assertThat(git.refs(repo).stashes()).isEmpty();
        assertThat(git.status(repo).changes()).extracting(c -> c.path())
                .containsExactlyInAnyOrder("f.md", "new.txt");
    }

    @Test
    void goingBackRefusesToDropAChangedNewFile() throws Exception {
        StashedSwitch conflicted = switchAwayFromFileOnlyOnFeature();
        write("new.txt", "edited after the switch\n");

        assertThatThrownBy(() -> git.goBackFromStashedSwitch(repo, conflicted))
                .isInstanceOf(GitException.class).hasMessageContaining("new.txt");
        assertThat(git.currentBranch(repo)).hasValue(main);
        assertThat(repo.resolve("new.txt")).hasContent("edited after the switch\n");
        assertThat(git.refs(repo).stashes()).hasSize(1);
    }

    @Test
    void predictsChangesThatDoNotFitTheOtherBranch() throws Exception {
        changeFileOnlyOnFeature();
        git.switchBranch(repo, main);
        commit("a.txt", BASE.replace("1\n", "1 main\n"), "main change");
        commit("taken.txt", "main\n", "main file");
        git.switchBranch(repo, "feature");
        write("f.md", "feature local\n");
        write("a.txt", BASE.replace("1\n", "1 local\n"));
        write("taken.txt", "local\n");
        write("free.txt", "local\n");

        assertThat(git.predictSwitchClashes(repo, main, null)).hasValueSatisfying(clashes ->
                assertThat(clashes).containsExactlyInAnyOrder(
                        new SwitchClash("f.md", SwitchClash.Reason.MISSING_THERE),
                        new SwitchClash("a.txt", SwitchClash.Reason.CHANGED_THERE),
                        new SwitchClash("taken.txt", SwitchClash.Reason.EXISTS_THERE)));
        assertThat(git.refs(repo).stashes()).isEmpty();
        assertThat(repo.resolve("f.md")).hasContent("feature local\n");
        assertThat(git.status(repo).changes()).hasSize(4);
    }

    @Test
    void predictsNothingWhenTheChangesFit() throws Exception {
        init();
        branchChangingFirstLine();
        write("a.txt", BASE.replace("8\n", "8 local\n"));

        assertThat(git.predictSwitchClashes(repo, "other", null)).hasValue(List.of());
    }

    @Test
    void canSwitchAndKeepTheChangesInTheStash() throws Exception {
        changeFileOnlyOnFeature();
        write("f.md", "feature local\n");

        StashedSwitch result = git.switchBranchWithStash(repo, main, null, true, false);

        assertThat(result.outcome()).isEqualTo(Outcome.STASHED);
        assertThat(result.switched()).isTrue();
        assertThat(git.currentBranch(repo)).hasValue(main);
        assertThat(git.status(repo).changes()).isEmpty();
        assertThat(git.refs(repo).stashes()).hasSize(1);
        assertThat(result.log()).extracting(LoggedCommand::command).hasSize(2);
    }

    /** Like a design note added on a feature branch, changed locally, then switching to main. */
    private StashedSwitch switchAwayFromFileOnlyOnFeature() throws Exception {
        changeFileOnlyOnFeature();
        write("f.md", "feature local\n");
        write("new.txt", "new\n");
        return git.switchBranchWithStash(repo, main, null, true, true);
    }

    private void changeFileOnlyOnFeature() throws Exception {
        init();
        git.createBranch(repo, "feature", true);
        commit("f.md", "feature\n", "note");
    }

    private void branchChangingFirstLine() throws Exception {
        git.createBranch(repo, "other", true);
        commit("a.txt", BASE.replace("1\n", "1 other\n"), "other change");
        git.switchBranch(repo, main);
    }

    private void init() throws Exception {
        git.init(repo);
        try (Git jgit = Git.open(repo.toFile())) {
            StoredConfig config = jgit.getRepository().getConfig();
            config.setString("user", null, "name", "Test");
            config.setString("user", null, "email", "test@example.invalid");
            config.setBoolean("commit", null, "gpgsign", false);
            config.setString("core", null, "hooksPath", "no-hooks");
            config.save();
        }
        commit("a.txt", BASE, "base");
        main = git.currentBranch(repo).orElseThrow();
    }

    private void write(String path, String content) throws Exception {
        Files.writeString(repo.resolve(path), content, StandardCharsets.UTF_8);
    }

    private void commit(String path, String content, String message) throws Exception {
        write(path, content);
        git.commit(repo, git.status(repo).changes(), message);
    }
}
