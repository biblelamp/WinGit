package cz.wingit.core;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import cz.wingit.core.cli.GitCli;

class GitIdentityTest {

    @TempDir
    Path tempDir;

    @Test
    void readsNameAndEmailFromTheGlobalConfiguration() throws Exception {
        Path config = tempDir.resolve("gitconfig");
        Files.writeString(config, "[user]\n\tname = Jana Nováková\n\temail = jana@example.invalid\n",
                StandardCharsets.UTF_8);

        GitIdentity identity = DefaultGitService.readGlobalIdentity(new GitCli(),
                Map.of("GIT_CONFIG_GLOBAL", config.toString()));

        assertThat(identity).isEqualTo(new GitIdentity("Jana Nováková", "jana@example.invalid"));
    }

    @Test
    void missingValuesAreEmpty() throws Exception {
        Path config = tempDir.resolve("gitconfig");
        Files.writeString(config, "[core]\n\tautocrlf = true\n", StandardCharsets.UTF_8);

        assertThat(DefaultGitService.readGlobalIdentity(new GitCli(), Map.of("GIT_CONFIG_GLOBAL", config.toString())))
                .isEqualTo(GitIdentity.NONE);
        assertThat(DefaultGitService.readGlobalIdentity(new GitCli(),
                Map.of("GIT_CONFIG_GLOBAL", tempDir.resolve("missing").toString())))
                .isEqualTo(GitIdentity.NONE);
    }

    @Test
    void repositoryValuesOverrideGlobalOnesAndAreMarkedAsOwn() throws Exception {
        Path config = tempDir.resolve("gitconfig");
        Files.writeString(config, "[user]\n\tname = Jana Nováková\n\temail = jana@example.invalid\n",
                StandardCharsets.UTF_8);
        Path repo = Files.createDirectory(tempDir.resolve("repo"));
        Map<String, String> env = Map.of("GIT_CONFIG_GLOBAL", config.toString());
        GitCli cli = new GitCli();
        cli.run(repo, List.of("init", "-q"), null, env, null);

        assertThat(DefaultGitService.readRepositoryIdentity(cli, repo, env)).isEqualTo(
                new RepositoryIdentity(new GitIdentity("Jana Nováková", "jana@example.invalid"), false, false));

        cli.run(repo, List.of("config", "--local", "user.email", "jana@work.invalid"), null, env, null);
        assertThat(DefaultGitService.readRepositoryIdentity(cli, repo, env)).isEqualTo(
                new RepositoryIdentity(new GitIdentity("Jana Nováková", "jana@work.invalid"), false, true));
    }

    @Test
    void parsesScopedOutput() {
        String out = "global" + '\0' + "user.name\nJana" + '\0' + "global" + '\0' + "user.email\nj@a.invalid" + '\0'
                + "local" + '\0' + "user.name\nJana W." + '\0';

        assertThat(DefaultGitService.parseScopedIdentity(out))
                .isEqualTo(new RepositoryIdentity(new GitIdentity("Jana W.", "j@a.invalid"), true, false));
        assertThat(DefaultGitService.parseScopedIdentity(""))
                .isEqualTo(new RepositoryIdentity(GitIdentity.NONE, false, false));
    }

    @Test
    void parsesNulSeparatedOutputAndTheLastValueWins() {
        String out = "user.name\nOld Name" + '\0' + "user.email\na@b.invalid" + '\0' + "user.name\nNew Name" + '\0';

        assertThat(DefaultGitService.parseIdentity(out)).isEqualTo(new GitIdentity("New Name", "a@b.invalid"));
        assertThat(DefaultGitService.parseIdentity("")).isEqualTo(GitIdentity.NONE);
    }
}
