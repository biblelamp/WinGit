package cz.wingit.ui.common;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import cz.wingit.core.GitCommandException;
import cz.wingit.core.remote.SshFailures.Kind;
import cz.wingit.core.remote.SshFailures.SshFailure;

class SshFailureTextTest {

    @Test
    void everyKindIsExplainedWithoutMissingTexts() {
        for (Kind kind : Kind.values()) {
            String full = SshFailureText.explain(new SshFailure(kind, "github.com", "SHA256:abc",
                    "~/.ssh/known_hosts:4", "C:/keys/id", "invalid format"));
            String bare = SshFailureText.explain(new SshFailure(kind, null, null, null, null, null));
            for (String text : new String[] {full, bare}) {
                assertThat(text).doesNotContain("ssh.problem.").doesNotContain("{0}").doesNotContain("{1}");
            }
        }
    }

    @Test
    void changedHostKeyNamesTheFingerprintAndTheCommand() {
        String text = SshFailureText.explain(new SshFailure(Kind.HOST_KEY_CHANGED, "github.com", "SHA256:abc",
                "~/.ssh/known_hosts:4", null, null));
        assertThat(text).contains("SHA256:abc").contains("ssh-keygen -R github.com")
                .contains("~/.ssh/known_hosts:4").contains("GitHub's SSH key fingerprints");
    }

    @Test
    void gitErrorsWithAnSshProblemAreExplainedAndKeepGitsOutput() {
        String stderr = "git@github.com: Permission denied (publickey).\nfatal: Could not read from remote repository.";
        String text = Dialogs.describe(new GitCommandException("git push", 128, stderr));
        assertThat(text).startsWith(SshFailureText.explain(new SshFailure(Kind.KEY_REJECTED, "github.com",
                null, null, null, null))).endsWith(stderr);

        assertThat(Dialogs.describe(new GitCommandException("git push", 1, "! [rejected] main -> main\n")))
                .isEqualTo("! [rejected] main -> main");
    }
}
