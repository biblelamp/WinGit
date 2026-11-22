package cz.wingit.app;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;

class RestarterTest {

    private static final Optional<String> JAVA = Optional.of("C:\\jdk\\bin\\javaw.exe");

    @Test
    void jpackageLauncherStartsItself() {
        Map<String, String> properties = Map.of(
                "jpackage.app-path", "C:\\Program Files\\WinGit\\WinGit.exe",
                "sun.java.command", "cz.wingit.app.Main");

        assertThat(Restarter.command(properties::get, JAVA))
                .contains(List.of("C:\\Program Files\\WinGit\\WinGit.exe"));
    }

    @Test
    void mainClassIsStartedWithTheSameClassPath() {
        Map<String, String> properties = Map.of(
                "sun.java.command", "cz.wingit.app.Main --some-arg",
                "java.class.path", "app.jar;ui.jar");

        assertThat(Restarter.command(properties::get, JAVA))
                .contains(List.of("C:\\jdk\\bin\\javaw.exe", "-cp", "app.jar;ui.jar", "cz.wingit.app.Main"));
    }

    @Test
    void jarPathMayContainSpaces() {
        Map<String, String> properties = Map.of("sun.java.command", "C:\\My Apps\\wingit.jar arg");

        assertThat(Restarter.command(properties::get, JAVA))
                .contains(List.of("C:\\jdk\\bin\\javaw.exe", "-jar", "C:\\My Apps\\wingit.jar"));
    }

    @Test
    void foreignLauncherCannotRestart() {
        // mvn exec:java runs the app inside Maven's own JVM
        Map<String, String> properties = Map.of(
                "sun.java.command", "org.codehaus.plexus.classworlds.launcher.Launcher -q exec:java");

        assertThat(Restarter.command(properties::get, JAVA)).isEmpty();
        assertThat(Restarter.command(key -> null, JAVA)).isEmpty();
    }
}
