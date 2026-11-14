package cz.wingit.app;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AppSettingsTest {

    @TempDir
    Path tempDir;

    @Test
    void missingFileGivesNoLastRepository() {
        assertThat(AppSettings.load(tempDir.resolve("settings.properties")).lastOpenedRepository()).isEmpty();
    }

    @Test
    void lastOpenedRepositoryIsPersisted() {
        Path file = tempDir.resolve("data/settings.properties");
        Path repo = tempDir.resolve("Projekty/čeština repo");

        AppSettings.load(file).setLastOpenedRepository(Optional.of(repo));
        assertThat(AppSettings.load(file).lastOpenedRepository()).contains(repo);

        AppSettings.load(file).setLastOpenedRepository(Optional.empty());
        assertThat(AppSettings.load(file).lastOpenedRepository()).isEmpty();
    }

    @Test
    void corruptFileFallsBackToDefaults() throws Exception {
        Path file = tempDir.resolve("settings.properties");
        Files.writeString(file, "lastOpenedRepository=\\u12", StandardCharsets.UTF_8); // malformed escape

        assertThat(AppSettings.load(file).lastOpenedRepository()).isEmpty();
    }
}
