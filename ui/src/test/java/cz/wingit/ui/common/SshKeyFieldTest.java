package cz.wingit.ui.common;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SshKeyFieldTest {

    @TempDir
    Path dir;

    @Test
    void blankMeansNoKeyAndQuotesOfCopyAsPathAreDropped() {
        assertThat(SshKeyField.parse("  ")).isEmpty();
        assertThat(SshKeyField.parse(" \"C:/keys/id work\" ")).hasValue(Path.of("C:/keys/id work"));
        assertThat(SshKeyField.parse("keys/id")).hasValue(Path.of("keys/id"));
    }

    @Test
    void keepsTheKeySetAndReportsItBack() {
        SshKeyField field = new SshKeyField("default");
        assertThat(field.key()).isEmpty();
        assertThat(field.isInvalid()).isFalse();

        field.setKey(Optional.of(dir.resolve("id")));
        assertThat(field.key()).hasValue(dir.resolve("id"));

        field.setKey(Optional.empty());
        assertThat(field.textField().getText()).isEmpty();
    }

    @Test
    void aPublicKeyPickedByMistakeBecomesThePrivateOneNextToIt() throws Exception {
        Path publicKey = Files.writeString(dir.resolve("id_ed25519.pub"), "ssh-ed25519 AAAA");
        assertThat(SshKeyField.publicToPrivate(publicKey)).isEqualTo(publicKey); // no private key there

        Path privateKey = Files.writeString(dir.resolve("id_ed25519"), "-----BEGIN OPENSSH PRIVATE KEY-----");
        assertThat(SshKeyField.publicToPrivate(publicKey)).isEqualTo(privateKey);
        assertThat(SshKeyField.publicToPrivate(privateKey)).isEqualTo(privateKey);
    }
}
