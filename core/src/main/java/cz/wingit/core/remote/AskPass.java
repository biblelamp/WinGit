package cz.wingit.core.remote;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import cz.wingit.core.GitException;

/**
 * Passes {@link Credentials} to a {@code git} process without putting them on the command line
 * or on disk: Git runs a small {@code GIT_ASKPASS} script that echoes them from environment
 * variables of that process only. Stored credential helpers are switched off for the call,
 * so stale saved credentials do not take precedence over what the user just entered.
 */
public final class AskPass {

    static final String USERNAME_VARIABLE = "WINGIT_ASKPASS_USERNAME";
    static final String PASSWORD_VARIABLE = "WINGIT_ASKPASS_PASSWORD";

    /**
     * Git asks with "Username for '...': " or "Password for '...': ". Delayed expansion prints
     * the values literally (also {@code & | < > ^ % !}); code page 65001 keeps non-ASCII as UTF-8.
     */
    private static final String WINDOWS_SCRIPT = String.join("\r\n",
            "@echo off",
            "setlocal EnableDelayedExpansion",
            "chcp 65001 >nul",
            "set \"WINGIT_PROMPT=%~1\"",
            "if /i \"!WINGIT_PROMPT:~0,8!\"==\"Username\" (",
            "  echo(!" + USERNAME_VARIABLE + "!",
            ") else (",
            "  echo(!" + PASSWORD_VARIABLE + "!",
            ")",
            "");

    private static final String POSIX_SCRIPT = String.join("\n",
            "#!/bin/sh",
            "case \"$1\" in",
            "  Username*) printf '%s\n' \"$" + USERNAME_VARIABLE + "\" ;;",
            "  *) printf '%s\n' \"$" + PASSWORD_VARIABLE + "\" ;;",
            "esac",
            "");

    private static Path script;

    private AskPass() {
    }

    /** Git options that disable configured credential helpers (must precede the subcommand). */
    public static List<String> gitOptions() {
        return List.of("-c", "credential.helper=");
    }

    /**
     * Environment for an attempt without entered credentials: saved credentials (Git Credential
     * Manager, other helpers) are used, but GCM shows no window of its own; if nothing is saved the
     * command fails with an authentication error and WinGit asks with its own sign-in dialog.
     */
    public static Map<String, String> storedOnlyEnvironment() {
        return Map.of("GCM_INTERACTIVE", "never");
    }

    /** Environment that makes Git answer its credential prompts with {@code credentials}. */
    public static Map<String, String> environment(Credentials credentials) {
        return Map.of(
                "GIT_ASKPASS", script().toString(),
                USERNAME_VARIABLE, credentials.username(),
                PASSWORD_VARIABLE, credentials.password(),
                "GCM_INTERACTIVE", "never");
    }

    /** {@link #gitOptions()} followed by {@code args}. */
    public static List<String> withOptions(List<String> args) {
        List<String> all = new ArrayList<>(gitOptions());
        all.addAll(args);
        return all;
    }

    /** The script is created once per run in the temp folder and removed on exit. */
    static synchronized Path script() {
        if (script != null && Files.isRegularFile(script)) {
            return script;
        }
        boolean windows = System.getProperty("os.name", "").toLowerCase(Locale.ROOT).startsWith("windows");
        try {
            Path file = Files.createTempFile("wingit-askpass-", windows ? ".cmd" : ".sh");
            Files.writeString(file, windows ? WINDOWS_SCRIPT : POSIX_SCRIPT, StandardCharsets.US_ASCII);
            if (!windows && !file.toFile().setExecutable(true, true)) {
                throw new IOException("cannot make " + file + " executable");
            }
            file.toFile().deleteOnExit();
            script = file;
            return file;
        } catch (IOException e) {
            throw new GitException("Cannot prepare credential prompt script: " + e.getMessage(), e);
        }
    }
}
