package cz.wingit.core;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import cz.wingit.core.cli.GitCli;

class GitVersionTest {

    @Test
    void installedGitReportsItsVersion() {
        assertThat(new DefaultGitService(new GitCli()).gitVersion()).matches("\\d+\\.\\d+.*");
    }

    @Test
    void versionIsTakenFromTheFirstLine() {
        assertThat(DefaultGitService.parseGitVersion("git version 2.47.1.windows.1\n"))
                .isEqualTo("2.47.1.windows.1");
        assertThat(DefaultGitService.parseGitVersion("git version 2.39.5 (Apple Git-154)"))
                .isEqualTo("2.39.5 (Apple Git-154)");
        assertThat(DefaultGitService.parseGitVersion("something else")).isEqualTo("something else");
        assertThat(DefaultGitService.parseGitVersion("")).isEmpty();
    }
}
