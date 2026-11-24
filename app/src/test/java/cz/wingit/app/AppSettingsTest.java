package cz.wingit.app;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import cz.wingit.ui.Language;
import cz.wingit.ui.Theme;
import cz.wingit.ui.main.WindowLayout;
import cz.wingit.ui.settings.Appearance;
import cz.wingit.ui.settings.FetchInterval;
import cz.wingit.ui.settings.Preferences;

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
    void lastSelectedFileIsPersisted() {
        Path file = tempDir.resolve("settings.properties");
        assertThat(AppSettings.load(file).lastSelectedFile()).isEmpty();

        AppSettings.load(file).setLastSelectedFile(Optional.of("ui/src/čeština [1].java"));
        assertThat(AppSettings.load(file).lastSelectedFile()).contains("ui/src/čeština [1].java");

        AppSettings.load(file).setLastSelectedFile(Optional.empty());
        assertThat(AppSettings.load(file).lastSelectedFile()).isEmpty();
    }

    @Test
    void windowLayoutIsPersisted() {
        Path file = tempDir.resolve("settings.properties");
        assertThat(AppSettings.load(file).windowLayout()).isEmpty();

        WindowLayout maximized = new WindowLayout(-1500, 20, 1200, 800, true,
                Map.of("main", new WindowLayout.Divider(300, 1180), "left", new WindowLayout.Divider(410, 690)));
        AppSettings.load(file).setWindowLayout(maximized);
        assertThat(AppSettings.load(file).windowLayout()).contains(maximized);

        // A later save replaces the dividers instead of merging stale ones.
        WindowLayout normal = new WindowLayout(10, 20, 900, 700, false,
                Map.of("main", new WindowLayout.Divider(250, 880)));
        AppSettings.load(file).setWindowLayout(normal);
        assertThat(AppSettings.load(file).windowLayout()).contains(normal);
    }

    @Test
    void appearanceIsPersisted() {
        Path file = tempDir.resolve("settings.properties");
        assertThat(AppSettings.load(file).appearance()).isEqualTo(Appearance.DEFAULT);

        Appearance chosen = new Appearance(Language.CZECH, Theme.Mode.DARK);
        AppSettings.load(file).setAppearance(chosen);
        assertThat(AppSettings.load(file).appearance()).isEqualTo(chosen);
    }

    @Test
    void fetchIntervalIsPersistedWithTheAppearance() throws Exception {
        Path file = tempDir.resolve("settings.properties");
        assertThat(AppSettings.load(file).preferences()).isEqualTo(Preferences.DEFAULT);

        Preferences chosen = new Preferences(new Appearance(Language.CZECH, Theme.Mode.DARK),
                new FetchInterval(false, 25));
        AppSettings.load(file).setPreferences(chosen);
        assertThat(AppSettings.load(file).preferences()).isEqualTo(chosen);

        Files.writeString(file, "fetch.auto=true\nfetch.intervalMinutes=99999\n", StandardCharsets.UTF_8);
        assertThat(AppSettings.load(file).fetchInterval()).isEqualTo(new FetchInterval(true, FetchInterval.MAX_MINUTES));
        Files.writeString(file, "fetch.intervalMinutes=often\n", StandardCharsets.UTF_8);
        assertThat(AppSettings.load(file).fetchInterval()).isEqualTo(FetchInterval.DEFAULT);
    }

    @Test
    void unknownAppearanceValuesGiveDefaults() throws Exception {
        Path file = tempDir.resolve("settings.properties");
        Files.writeString(file, "ui.language=klingon\nui.theme=purple\n", StandardCharsets.UTF_8);

        assertThat(AppSettings.load(file).appearance()).isEqualTo(Appearance.DEFAULT);
    }

    @Test
    void brokenWindowLayoutIsIgnored() throws Exception {
        Path file = tempDir.resolve("settings.properties");
        Files.writeString(file, "window.x=10\nwindow.y=abc\nwindow.width=900\nwindow.height=700\n",
                StandardCharsets.UTF_8);

        assertThat(AppSettings.load(file).windowLayout()).isEmpty();
    }

    @Test
    void corruptFileFallsBackToDefaults() throws Exception {
        Path file = tempDir.resolve("settings.properties");
        Files.writeString(file, "lastOpenedRepository=\\u12", StandardCharsets.UTF_8); // malformed escape

        assertThat(AppSettings.load(file).lastOpenedRepository()).isEmpty();
    }
}
