package cz.wingit.core.remote;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import cz.wingit.core.cli.GitCli;
import cz.wingit.core.cli.GitResult;

class AskPassTest {

    @TempDir
    Path tempDir;

    @Test
    void gitReceivesUserNameAndPasswordFromScript() {
        // 'git credential fill' prompts through GIT_ASKPASS exactly like a push or fetch over HTTPS.
        Credentials credentials = new Credentials("joe", "p&a|s^s%PATH%!x<y>\"z\" ěščř");

        GitResult result = new GitCli().run(tempDir, AskPass.withOptions(List.of("credential", "fill")), null,
                AskPass.environment(credentials), "protocol=https\nhost=example.invalid\n\n");

        assertThat(result.stdout().lines())
                .contains("username=joe", "password=" + credentials.password());
    }

    @Test
    void toStringHidesPassword() {
        assertThat(new Credentials("joe", "secret").toString()).doesNotContain("secret").contains("joe");
    }

    @Test
    void recognizesAuthenticationFailures() {
        assertThat(AuthFailures.isAuthenticationFailure(
                "fatal: could not read Username for 'https://github.com': terminal prompts disabled")).isTrue();
        assertThat(AuthFailures.isAuthenticationFailure(
                "remote: Invalid username or password.\nfatal: Authentication failed for 'https://x/y.git/'"))
                .isTrue();
        assertThat(AuthFailures.isAuthenticationFailure(
                "fatal: unable to access 'https://x/': The requested URL returned error: 403")).isTrue();
        assertThat(AuthFailures.isAuthenticationFailure(
                "! [rejected]        main -> main (fetch first)")).isFalse();
        assertThat(AuthFailures.isAuthenticationFailure(
                "fatal: Cannot prompt because user interactivity has been disabled.")).isTrue();
        assertThat(AuthFailures.isAuthenticationFailure(null)).isFalse();
    }
}
