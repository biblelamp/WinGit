package cz.wingit.ui.reposettings;

import static org.assertj.core.api.Assertions.assertThat;

import java.awt.GraphicsEnvironment;
import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.ExecutionException;

import javax.swing.SwingUtilities;

import org.eclipse.jgit.api.Git;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.formdev.flatlaf.FlatClientProperties;

import cz.wingit.core.DefaultGitService;
import cz.wingit.core.GitIdentity;
import cz.wingit.core.RepositoryIdentity;
import cz.wingit.core.cli.GitCli;
import cz.wingit.core.remote.RemoteUrls;
import cz.wingit.core.remote.SshCommand;
import cz.wingit.ui.Messages;

class RepositorySettingsDialogTest {

    @TempDir
    Path repo;

    private RepositorySettingsDialog dialog;

    /** The dialog reads the repository in the background; let it finish before the folder is deleted. */
    @AfterEach
    void waitForTheRead() throws Exception {
        if (dialog != null) {
            try {
                dialog.reader().get();
            } catch (ExecutionException e) {
                // reading failed: nothing holds the folder any more
            }
            SwingUtilities.invokeAndWait(() -> { });
        }
    }

    @BeforeEach
    void setUp() throws Exception {
        Assumptions.assumeFalse(GraphicsEnvironment.isHeadless(), "needs a display");
        Git.init().setDirectory(repo.toFile()).call().close();
    }

    @Test
    void withoutOwnKeyTheApplicationSettingApplies() throws Exception {
        onEdt(() -> {
            dialog = new RepositorySettingsDialog(null, new DefaultGitService(new GitCli()), repo, Optional.empty());
            dialog.show(null, null);
            assertThat(dialog.inheritButton().isSelected()).isTrue();
            assertThat(dialog.keyField().isEnabled()).isFalse();
            assertThat(dialog.keyChanged()).isFalse();

            dialog.ownButton().doClick();
            dialog.keyField().setKey(Optional.of(Path.of("id_work")));
            assertThat(dialog.keyField().isEnabled()).isTrue();
            assertThat(dialog.keyChanged()).isTrue();
            assertThat(dialog.chosenKey()).hasValue(Path.of("id_work"));
            dialog.dispose();
        });
    }

    @Test
    void ownKeyIsShownAndAHandWrittenCommandStaysUntilChanged() throws Exception {
        onEdt(() -> {
            dialog = new RepositorySettingsDialog(null, new DefaultGitService(new GitCli()), repo, Optional.empty());
            dialog.show("https://example.invalid/r.git", SshCommand.forKey(Path.of("id_work"), false));
            assertThat(dialog.ownButton().isSelected()).isTrue();
            assertThat(dialog.keyField().key()).hasValue(Path.of("id_work"));
            assertThat(dialog.manualNote().getText().isBlank()).isTrue();
            assertThat(dialog.keyChanged()).isFalse();

            dialog.show("https://example.invalid/r.git", "ssh -p 2222 -i ~/.ssh/other");
            assertThat(dialog.manualNote().getText()).contains("ssh -p 2222");
            assertThat(dialog.keyChanged()).isFalse();

            dialog.inheritButton().doClick();
            assertThat(dialog.keyChanged()).isTrue();
            assertThat(dialog.chosenKey()).isEmpty();
            dialog.dispose();
        });
    }

    @Test
    void urlShowsHowGitSignsInAndOnlyAChangedUrlIsWritten() throws Exception {
        onEdt(() -> {
            dialog = new RepositorySettingsDialog(null, new DefaultGitService(new GitCli()), repo, Optional.empty());
            dialog.show(null, null);
            assertThat(dialog.urlNote().getText()).contains("origin");
            assertThat(dialog.changedUrl()).isEmpty();
            assertThat(dialog.validationError()).isNull();

            dialog.show("https://example.invalid/r.git", null);
            assertThat(dialog.urlNote().getText()).isEqualTo(Messages.get("repoSettings.url.https"));
            assertThat(dialog.changedUrl()).isEmpty();

            dialog.urlField().setText("git@example.invalid:user/r.git");
            assertThat(dialog.urlNote().getText()).isEqualTo(Messages.get("repoSettings.url.ssh"));
            assertThat(dialog.changedUrl()).hasValue("git@example.invalid:user/r.git");

            dialog.urlField().setText(" ");
            assertThat(dialog.validationError()).isEqualTo(Messages.get("repoSettings.url.missing"));
            dialog.urlField().setText("-x");
            assertThat(dialog.validationError()).isEqualTo(Messages.get("repoSettings.url.invalid"));
            dialog.dispose();
        });
    }

    @Test
    void ownUserValuesAreEditableAndGlobalOnesShowAsPlaceholders() throws Exception {
        onEdt(() -> {
            dialog = new RepositorySettingsDialog(null, new DefaultGitService(new GitCli()), repo, Optional.empty());
            dialog.show(null, null);
            dialog.showIdentity(new RepositoryIdentity(new GitIdentity("Repo User", "global@example.invalid"),
                    true, false), new GitIdentity("Global User", "global@example.invalid"));
            assertThat(dialog.nameField().getText()).isEqualTo("Repo User");
            assertThat(dialog.nameField().isEditable()).isTrue();
            assertThat(dialog.emailField().getText()).isEmpty();
            assertThat(dialog.emailField().getClientProperty(FlatClientProperties.PLACEHOLDER_TEXT))
                    .isEqualTo(Messages.format("repoSettings.user.inherited", "global@example.invalid"));
            assertThat(dialog.identityChanged()).isFalse();

            dialog.emailField().setText(" repo@example.invalid ");
            assertThat(dialog.identityChanged()).isTrue();
            dialog.emailField().setText(" ");
            assertThat(dialog.identityChanged()).isFalse();
            dialog.nameField().setText("");
            assertThat(dialog.identityChanged()).isTrue();
            dialog.dispose();
        });
    }

    @Test
    void everyTransportHasANote() {
        for (RemoteUrls.Transport transport : RemoteUrls.Transport.values()) {
            assertThat(Messages.get(RepositorySettingsDialog.transportNoteKey(transport))).doesNotContain("repoSettings.");
        }
    }

    private static void onEdt(Runnable test) throws Exception {
        Throwable[] failure = {null};
        SwingUtilities.invokeAndWait(() -> {
            try {
                test.run();
            } catch (Throwable t) {
                failure[0] = t;
            }
        });
        if (failure[0] instanceof AssertionError error) {
            throw error;
        }
        if (failure[0] != null) {
            throw new AssertionError(failure[0]);
        }
    }
}
