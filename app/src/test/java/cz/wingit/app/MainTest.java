package cz.wingit.app;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class MainTest {

    @Test
    void versionComesFromMavenWhenNotLaunchedByJpackage() {
        assertThat(System.getProperty("jpackage.app-version")).isNull();
        assertThat(Main.version()).matches("\\d+\\.\\d+\\.\\d+.*");
    }
}
