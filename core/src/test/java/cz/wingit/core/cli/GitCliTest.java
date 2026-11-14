package cz.wingit.core.cli;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import cz.wingit.core.GitCommandException;
import cz.wingit.core.GitException;

class GitCliTest {

    @TempDir
    Path tempDir;

    @Test
    void runsGit() {
        GitResult result = new GitCli().run(tempDir, List.of("--version"));

        assertThat(result.exitCode()).isZero();
        assertThat(result.stdout()).startsWith("git version");
    }

    @Test
    void nonZeroExitBecomesTypedException() {
        assertThatThrownBy(() -> new GitCli().run(tempDir, List.of("rev-parse", "HEAD")))
                .isInstanceOfSatisfying(GitCommandException.class, e -> {
                    assertThat(e.exitCode()).isEqualTo(128);
                    assertThat(e.command()).isEqualTo("git rev-parse HEAD");
                    assertThat(e.stderr()).contains("not a git repository");
                });
    }

    @Test
    void missingExecutableIsReported() {
        assertThatThrownBy(() -> new GitCli("wingit-no-such-git").run(tempDir, List.of("--version")))
                .isExactlyInstanceOf(GitException.class)
                .hasMessageContaining("Cannot start Git");
    }
}
