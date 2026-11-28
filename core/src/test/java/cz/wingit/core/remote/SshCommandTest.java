package cz.wingit.core.remote;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;

import org.junit.jupiter.api.Test;

class SshCommandTest {

    @Test
    void quotesTheKeyForTheShellWithForwardSlashes() {
        assertThat(SshCommand.forKey(Path.of("keys", "it's mine"), false))
                .isEqualTo("ssh -i 'keys/it'\\''s mine' -o IdentitiesOnly=yes");
        assertThat(SshCommand.forKey(null, true)).isEqualTo("ssh -o BatchMode=yes");
        assertThat(SshCommand.forKey(Path.of("k"), true)).isEqualTo("ssh -o BatchMode=yes -i 'k' -o IdentitiesOnly=yes");
    }

    @Test
    void readsTheKeyBackFromWhatItWrote() {
        Path key = Path.of("keys", "it's mine");
        String command = SshCommand.forKey(key, false);

        assertThat(SshCommand.keyOf(command)).hasValue(Path.of("keys/it's mine"));
        assertThat(SshCommand.isPlainKeyCommand(command)).isTrue();
    }

    @Test
    void readsKeysOfCommandsWrittenByHand() {
        assertThat(SshCommand.keyOf("ssh -i ~/.ssh/work_key")).hasValue(Path.of("~/.ssh/work_key"));
        assertThat(SshCommand.keyOf("ssh -p 2222 -i \"C:/My Keys/id\" -v")).hasValue(Path.of("C:/My Keys/id"));
        assertThat(SshCommand.keyOf("ssh -iC:/keys/id")).hasValue(Path.of("C:/keys/id"));
        assertThat(SshCommand.keyOf("plink -batch")).isEmpty();
        assertThat(SshCommand.isPlainKeyCommand("ssh -p 2222 -i 'k' -o IdentitiesOnly=yes")).isFalse();
        assertThat(SshCommand.isPlainKeyCommand("ssh -v")).isFalse();
    }

    @Test
    void splitsLikeAShell() {
        assertThat(SshCommand.split("a  'b c' \"d \\\" e\" f\\ g ''"))
                .containsExactly("a", "b c", "d \" e", "f g", "");
    }
}
