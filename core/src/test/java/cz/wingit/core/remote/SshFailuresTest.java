package cz.wingit.core.remote;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import cz.wingit.core.remote.SshFailures.Kind;
import cz.wingit.core.remote.SshFailures.SshFailure;

class SshFailuresTest {

    private static final String FATAL = "fatal: Could not read from remote repository.\n\n"
            + "Please make sure you have the correct access rights\nand the repository exists.\n";

    @Test
    void changedHostKey() {
        String stderr = "@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@\n"
                + "@    WARNING: REMOTE HOST IDENTIFICATION HAS CHANGED!     @\n"
                + "@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@\n"
                + "IT IS POSSIBLE THAT SOMEONE IS DOING SOMETHING NASTY!\n"
                + "The fingerprint for the RSA key sent by the remote host is\n"
                + "SHA256:uNiVztksCsDhcc0u9e8BujQXVUpKZIDTMczCvj3tD2s.\n"
                + "Please contact your system administrator.\n"
                + "Add correct host key in /c/Users/me/.ssh/known_hosts to get rid of this message.\n"
                + "Offending RSA key in /c/Users/me/.ssh/known_hosts:4\n"
                + "Host key for github.com has changed and you have requested strict checking.\n"
                + "Host key verification failed.\n" + FATAL;

        assertThat(SshFailures.find(stderr)).hasValue(new SshFailure(Kind.HOST_KEY_CHANGED, "github.com",
                "SHA256:uNiVztksCsDhcc0u9e8BujQXVUpKZIDTMczCvj3tD2s", "/c/Users/me/.ssh/known_hosts:4", null, null));
    }

    @Test
    void unknownHost() {
        assertThat(SshFailures.find("No ED25519 host key is known for gitlab.example.com and you have requested "
                + "strict checking.\nHost key verification failed.\n" + FATAL))
                .hasValue(new SshFailure(Kind.HOST_KEY_UNKNOWN, "gitlab.example.com", null, null, null, null));
        assertThat(SshFailures.find("Host key verification failed.\r\n" + FATAL).map(SshFailure::kind))
                .hasValue(Kind.HOST_KEY_UNKNOWN);
    }

    @Test
    void keyProblems() {
        assertThat(SshFailures.find("Warning: Identity file C:/keys/id_gone not accessible: No such file or "
                + "directory.\ngit@github.com: Permission denied (publickey).\n" + FATAL))
                .hasValue(new SshFailure(Kind.KEY_FILE_MISSING, "github.com", null, null, "C:/keys/id_gone", null));
        assertThat(SshFailures.find("Load key \"C:/keys/id.pub\": invalid format\n"
                + "git@github.com: Permission denied (publickey).\n" + FATAL))
                .hasValue(new SshFailure(Kind.KEY_UNREADABLE, "github.com", null, null, "C:/keys/id.pub",
                        "invalid format"));
        assertThat(SshFailures.find("git@github.com: Permission denied (publickey).\r\n" + FATAL))
                .hasValue(new SshFailure(Kind.KEY_REJECTED, "github.com", null, null, null, null));
    }

    @Test
    void otherErrorsAreNotSshProblems() {
        assertThat(SshFailures.find(" ")).isEmpty();
        assertThat(SshFailures.find("remote: Repository not found.\nfatal: repository "
                + "'https://github.com/a/b.git/' not found\n")).isEmpty();
        assertThat(SshFailures.find("! [rejected]        main -> main (fetch first)\n")).isEmpty();
    }
}
