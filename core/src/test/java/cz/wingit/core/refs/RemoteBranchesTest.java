package cz.wingit.core.refs;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

class RemoteBranchesTest {

    @Test
    void passwordInTheUrlIsNeverShown() {
        assertThat(url("https://user:secret-token@github.com/org/repo.git"))
                .isEqualTo("https://user@github.com/org/repo.git");
        assertThat(url("https://ghp_token:x-oauth-basic@github.com/org/repo.git"))
                .isEqualTo("https://ghp_token@github.com/org/repo.git");
    }

    @Test
    void otherUrlsStayAsConfigured() {
        assertThat(url("https://github.com/org/repo.git")).isEqualTo("https://github.com/org/repo.git");
        assertThat(url("https://user@host:8443/repo.git")).isEqualTo("https://user@host:8443/repo.git");
        assertThat(url("ssh://git@host:2222/repo.git")).isEqualTo("ssh://git@host:2222/repo.git");
        assertThat(url("git@github.com:org/repo.git")).isEqualTo("git@github.com:org/repo.git");
        assertThat(url("C:\repos\server")).isEqualTo("C:\repos\server");
        assertThat(url(" ")).isNull();
        assertThat(url(null)).isNull();
    }

    private static String url(String configured) {
        return new RemoteBranches("origin", List.of(), configured).url();
    }
}
