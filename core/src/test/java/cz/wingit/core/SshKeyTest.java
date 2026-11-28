package cz.wingit.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;

import org.eclipse.jgit.api.Git;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import cz.wingit.core.cli.GitCli;
import cz.wingit.core.remote.SshCommand;

class SshKeyTest {

    @TempDir
    Path repo;

    private final DefaultGitService git = new DefaultGitService(new GitCli());

    @Test
    void repositoryKeyIsWrittenToItsOwnConfigurationAndRemoved() throws Exception {
        Git.init().setDirectory(repo.toFile()).call().close();
        Path key = repo.resolve("keys").resolve("id_ed25519");
        assertThat(git.repositorySshCommand(repo)).isEmpty();

        git.setRepositorySshKey(repo, Optional.of(key));

        Optional<String> command = git.repositorySshCommand(repo);
        assertThat(command).hasValue(SshCommand.forKey(key, false));
        assertThat(SshCommand.keyOf(command.get()).map(Path::toAbsolutePath)).hasValue(key.toAbsolutePath());
        try (Git opened = Git.open(repo.toFile())) {
            assertThat(DefaultGitService.sshConfigured(opened.getRepository().getConfig())).isTrue();
        }

        git.setRepositorySshKey(repo, Optional.empty());
        git.setRepositorySshKey(repo, Optional.empty()); // nothing to remove: no error

        assertThat(git.repositorySshCommand(repo)).isEmpty();
    }

    @Test
    void remoteUrlIsChangedOrTheRemoteAdded() throws Exception {
        Git.init().setDirectory(repo.toFile()).call().close();
        assertThat(git.remoteUrl(repo, "origin")).isEmpty();

        git.setRemoteUrl(repo, "origin", " https://example.invalid/r.git ");
        assertThat(git.remoteUrl(repo, "origin")).hasValue("https://example.invalid/r.git");

        git.setRemoteUrl(repo, "origin", "git@example.invalid:user/r.git");
        assertThat(git.remoteUrl(repo, "origin")).hasValue("git@example.invalid:user/r.git");

        assertThatThrownBy(() -> git.setRemoteUrl(repo, "origin", "--upload-pack=x"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> git.setRemoteUrl(repo, "origin", "  ")).isInstanceOf(IllegalArgumentException.class);
        assertThat(git.remoteUrl(repo, "origin")).hasValue("git@example.invalid:user/r.git");
    }

    @Test
    void repositoryUserIsWrittenAndRemovedInItsOwnConfiguration() throws Exception {
        Git.init().setDirectory(repo.toFile()).call().close();

        git.setRepositoryIdentity(repo, " Repo User ", "repo@example.invalid");
        RepositoryIdentity own = git.repositoryIdentity(repo);
        assertThat(own.identity()).isEqualTo(new GitIdentity("Repo User", "repo@example.invalid"));
        assertThat(own.ownName()).isTrue();
        assertThat(own.ownEmail()).isTrue();

        git.setRepositoryIdentity(repo, "", " ");
        git.setRepositoryIdentity(repo, null, null); // nothing to remove: no error
        RepositoryIdentity inherited = git.repositoryIdentity(repo);
        assertThat(inherited.ownName()).isFalse();
        assertThat(inherited.ownEmail()).isFalse();
    }

    @Test
    void globalKeyGoesToGitUnlessTheUserConfiguredSsh() {
        assertThat(git.sshEnvironment(false, false)).isEmpty();
        assertThat(git.sshEnvironment(false, true)).isEqualTo(Map.of("GIT_SSH_COMMAND", "ssh -o BatchMode=yes"));

        Path key = Path.of("id_work");
        git.setSshKey(Optional.of(key));

        assertThat(git.sshEnvironment(false, false))
                .isEqualTo(Map.of("GIT_SSH_COMMAND", SshCommand.forKey(key, false)));
        assertThat(git.sshEnvironment(false, true))
                .isEqualTo(Map.of("GIT_SSH_COMMAND", SshCommand.forKey(key, true)));
        assertThat(git.sshEnvironment(true, false)).isEmpty();
        assertThat(git.sshEnvironment(true, true)).isEmpty();
    }
}
