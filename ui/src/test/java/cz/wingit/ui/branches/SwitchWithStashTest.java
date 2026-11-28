package cz.wingit.ui.branches;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.PersonIdent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import cz.wingit.core.DefaultGitService;
import cz.wingit.core.GitCommandException;
import cz.wingit.core.GitService;
import cz.wingit.core.cli.GitCli;
import cz.wingit.core.cli.LoggedCommand;
import cz.wingit.core.refs.StashedSwitch;
import cz.wingit.core.refs.SwitchClash;
import cz.wingit.ui.common.CommandLogDialog;

class SwitchWithStashTest {

    private static final PersonIdent IDENT = new PersonIdent("Test", "test@example.invalid");

    @TempDir
    Path repo;

    @Test
    void everyOutcomeHasASummary() {
        for (StashedSwitch.Outcome outcome : StashedSwitch.Outcome.values()) {
            String summary = SwitchWithStash.summary("main",
                    new StashedSwitch(outcome, List.of("a.txt", "b.txt"), List.of()));
            assertThat(summary).doesNotContain("switchStash.").doesNotContain("{");
        }
        assertThat(SwitchWithStash.summary("main",
                new StashedSwitch(StashedSwitch.Outcome.CONFLICTS, List.of("a.txt", "b.txt"), List.of())))
                .contains("\"main\"").contains("2 file(s)");
    }

    @Test
    void transcriptLooksLikeAConsole() {
        Path dir = Path.of("repo");
        String text = CommandLogDialog.transcript(dir, List.of(
                new LoggedCommand("git switch main", 1, "error: local changes"),
                new LoggedCommand("git stash push", 0, "Saved working directory"),
                new LoggedCommand("git stash pop stash@{0}", 0, "")));

        assertThat(text).isEqualTo(dir + "> git switch main\nerror: local changes\n(exit code 1)\n"
                + "\n" + dir + "> git stash push\nSaved working directory\n"
                + "\n" + dir + "> git stash pop stash@{0}\n");
    }

    @Test
    void offeredOnlyWhenThereAreLocalChanges() throws Exception {
        try (Git git = Git.init().setDirectory(repo.toFile()).setInitialBranch("main").call()) {
            Files.writeString(repo.resolve("a.txt"), "a", StandardCharsets.UTF_8);
            git.add().addFilepattern("a.txt").call();
            git.commit().setMessage("init").setAuthor(IDENT).setCommitter(IDENT).setSign(false).call();
        }
        GitService service = new DefaultGitService(new GitCli());
        GitCommandException failure = catchThrowableOfType(() -> service.switchBranch(repo, "missing"),
                GitCommandException.class);

        assertThat(SwitchWithStash.check(service, repo, failure, "missing", null)).isEmpty();

        Files.writeString(repo.resolve("a.txt"), "changed", StandardCharsets.UTF_8);
        Files.writeString(repo.resolve("new.txt"), "new", StandardCharsets.UTF_8);
        assertThat(SwitchWithStash.check(service, repo, failure, "missing", null)).hasValueSatisfying(refusal -> {
            assertThat(refusal.changed()).isEqualTo(2);
            assertThat(refusal.untracked()).isEqualTo(1);
            assertThat(refusal.conflicts()).isEmpty();
        });
    }

    @Test
    void clashesAreListedWithTheirReason() {
        String text = SwitchWithStash.clashList("main", List.of(
                new SwitchClash("docs/f.md", SwitchClash.Reason.MISSING_THERE),
                new SwitchClash("a.txt", SwitchClash.Reason.CHANGED_THERE),
                new SwitchClash("new.txt", SwitchClash.Reason.EXISTS_THERE)));

        assertThat(text.split("\n")).hasSize(3).allMatch(line -> line.contains("\"main\""));
        assertThat(text).startsWith("docs/f.md").doesNotContain("switchStash.").doesNotContain("{");
    }

    @Test
    void conflictsAreNamedInsteadOfGitsMessage() {
        List<String> many = java.util.stream.IntStream.rangeClosed(1, 12).mapToObj(i -> "f" + i + ".md").toList();

        String text = SwitchWithStash.conflictsMessage("feature", many);

        assertThat(text).contains("\"feature\"").contains("12 file(s)").contains("    f1.md\n")
                .contains("    f10.md\n").doesNotContain("f11.md").contains("2 more").doesNotContain("{");
    }
}
