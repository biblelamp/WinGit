package cz.wingit.core.remote;

import java.util.List;
import java.util.Locale;

/**
 * Recognizes Git error output that means "the remote needs (other) credentials".
 */
public final class AuthFailures {

    private static final List<String> MARKERS = List.of(
            "authentication failed",
            "could not read username",
            "could not read password",
            "terminal prompts disabled",
            "user interactivity has been disabled",
            "invalid username or password",
            "http basic: access denied",
            "the requested url returned error: 401",
            "the requested url returned error: 403");

    private AuthFailures() {
    }

    /** {@code true} if {@code stderr} reports missing, wrong or insufficient HTTP credentials. */
    public static boolean isAuthenticationFailure(String stderr) {
        if (stderr == null) {
            return false;
        }
        String text = stderr.toLowerCase(Locale.ROOT);
        return MARKERS.stream().anyMatch(text::contains);
    }
}
