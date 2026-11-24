package cz.wingit.ui;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.swing.JFileChooser;
import javax.swing.JOptionPane;
import javax.swing.UIManager;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class MessagesTest {

    private static final Pattern PLACEHOLDER = Pattern.compile("\\{(\\d+)}");

    @Test
    void mainWindowTitleIsDefined() {
        assertThat(Messages.get("mainWindow.title")).isEqualTo("WinGit");
    }

    @ParameterizedTest
    @ValueSource(strings = {"ru", "cs"})
    void translationHasExactlyTheEnglishKeysAndPlaceholders(String language) {
        Properties english = file("messages.properties");
        Properties translated = file("messages_" + language + ".properties");

        assertThat(ownKeys(translated)).isEqualTo(ownKeys(english));
        for (String key : ownKeys(english)) {
            assertThat(placeholders(translated.getProperty(key)))
                    .as("placeholders of %s in %s", key, language)
                    .isEqualTo(placeholders(english.getProperty(key)));
        }
    }

    @Test
    void systemLanguageIsUsedOnlyWhenTranslated() {
        assertThat(Language.SYSTEM.resolve(Locale.forLanguageTag("cs-CZ"))).isEqualTo(Language.CZECH);
        assertThat(Language.SYSTEM.resolve(Locale.forLanguageTag("ru-RU"))).isEqualTo(Language.RUSSIAN);
        assertThat(Language.SYSTEM.resolve(Locale.GERMANY)).isEqualTo(Language.ENGLISH);
        assertThat(Language.RUSSIAN.resolve(Locale.GERMANY)).isEqualTo(Language.RUSSIAN);
    }

    @Test
    void languageCodesRoundTrip() {
        for (Language language : Language.values()) {
            assertThat(Language.fromCode(language.code())).isEqualTo(language);
        }
        assertThat(Language.fromCode(null)).isEqualTo(Language.SYSTEM);
        assertThat(Language.fromCode("klingon")).isEqualTo(Language.SYSTEM);
    }

    @Test
    void chosenLanguageDoesNotFallBackToTheSystemOne() {
        try {
            Messages.setLanguage(Language.CZECH);
            assertThat(Messages.get("menu.repository")).isEqualTo("Repozitář");
            assertThat(UIManager.getString("OptionPane.yesButtonText")).isEqualTo("Ano");
        } finally {
            Messages.setLanguage(Language.ENGLISH);
        }
        assertThat(Messages.get("menu.repository")).isEqualTo("Repository");
    }

    /** The translated Swing texts must not break the Yes/No questions (they did with Integer mnemonics). */
    @ParameterizedTest
    @ValueSource(strings = {"ENGLISH", "RUSSIAN", "CZECH"})
    void yesNoQuestionsWorkInEveryLanguage(String name) {
        Theme.install(Theme.Mode.LIGHT);
        try {
            Messages.setLanguage(Language.valueOf(name));
            JOptionPane pane = new JOptionPane("?", JOptionPane.QUESTION_MESSAGE, JOptionPane.YES_NO_OPTION);
            assertThat(pane.getUI()).isNotNull();
            new JFileChooser();
        } finally {
            Messages.setLanguage(Language.ENGLISH);
        }
    }

    /** The file itself, without the parent bundle's keys (so a missing translation shows). */
    private static Properties file(String name) {
        Properties properties = new Properties();
        try (InputStream in = Messages.class.getResourceAsStream(name)) {
            assertThat(in).as(name).isNotNull();
            properties.load(new InputStreamReader(in, StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return properties;
    }

    /** Keys of the UI itself; Swing's texts ({@code swing.}) exist only in translations. */
    private static Set<String> ownKeys(Properties properties) {
        Set<String> keys = new TreeSet<>();
        for (String key : properties.stringPropertyNames()) {
            if (!key.startsWith("swing.")) {
                keys.add(key);
            }
        }
        return keys;
    }

    private static Set<String> placeholders(String text) {
        Set<String> found = new TreeSet<>();
        Matcher matcher = PLACEHOLDER.matcher(text);
        while (matcher.find()) {
            found.add(matcher.group(1));
        }
        return found;
    }
}
