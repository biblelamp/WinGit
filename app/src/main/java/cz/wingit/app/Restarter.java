package cz.wingit.app;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.UnaryOperator;

/**
 * Starts a new instance of WinGit, so the settings that apply only at startup (language, theme) take effect.
 */
final class Restarter {

    private Restarter() {
    }

    /**
     * The command that starts WinGit the same way as now, or empty if it cannot be known (e.g. inside
     * {@code mvn exec:java}, where the JVM belongs to Maven).
     *
     * @param property    system property lookup
     * @param currentJava executable of the running JVM, if known
     */
    static Optional<List<String>> command(UnaryOperator<String> property, Optional<String> currentJava) {
        // The jpackage launcher (Windows .exe, Linux bin/WinGit) sets this to its own path.
        String launcher = property.apply("jpackage.app-path");
        if (launcher != null && !launcher.isBlank()) {
            return Optional.of(List.of(launcher));
        }
        String javaCommand = property.apply("sun.java.command");
        String java = currentJava.orElseGet(() -> property.apply("java.home") + File.separator + "bin"
                + File.separator + "java");
        if (javaCommand == null || javaCommand.isBlank()) {
            return Optional.empty();
        }
        String command = javaCommand.strip();
        if (command.split(" ", 2)[0].equals(Main.class.getName())) {
            return Optional.of(List.of(java, "-cp", property.apply("java.class.path"), Main.class.getName()));
        }
        // "java -jar C:\My Apps\wingit.jar args": the jar path may contain spaces.
        int jarEnd = command.toLowerCase(Locale.ROOT).indexOf(".jar") + ".jar".length();
        if (jarEnd > ".jar".length() && (jarEnd == command.length() || command.charAt(jarEnd) == ' ')) {
            return Optional.of(List.of(java, "-jar", command.substring(0, jarEnd)));
        }
        return Optional.empty();
    }

    /** {@link #command} of this JVM. */
    static Optional<List<String>> currentCommand() {
        return command(System::getProperty, ProcessHandle.current().info().command());
    }

    /**
     * Starts the new instance; the caller then exits.
     *
     * @throws UncheckedIOException if the process cannot be started
     */
    static void launch(List<String> command) {
        try {
            new ProcessBuilder(command)
                    .directory(new File(System.getProperty("user.dir")))
                    .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                    .redirectError(ProcessBuilder.Redirect.DISCARD)
                    .start();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
