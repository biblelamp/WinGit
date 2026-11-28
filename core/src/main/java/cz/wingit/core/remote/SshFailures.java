package cz.wingit.core.remote;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Recognizes OpenSSH problems in Git's error output, so they can be explained instead of shown raw.
 */
public final class SshFailures {

    /** The SSH problems WinGit explains. */
    public enum Kind {
        /** The server's host key differs from the one in {@code known_hosts}. */
        HOST_KEY_CHANGED,
        /** The server is not in {@code known_hosts} and nobody could be asked to trust it. */
        HOST_KEY_UNKNOWN,
        /** The chosen key file does not exist. */
        KEY_FILE_MISSING,
        /** The key file exists but ssh cannot use it (wrong format, passphrase, permissions). */
        KEY_UNREADABLE,
        /** The server refused every key offered. */
        KEY_REJECTED
    }

    /**
     * @param kind        what went wrong
     * @param host        the server, if the output names it
     * @param fingerprint fingerprint of the key the server sent ({@link Kind#HOST_KEY_CHANGED}), if shown
     * @param knownHosts  {@code known_hosts} file and line with the old key, e.g. {@code ~/.ssh/known_hosts:4}
     * @param keyFile     the key file ({@link Kind#KEY_FILE_MISSING}, {@link Kind#KEY_UNREADABLE}), if shown
     * @param reason      why the key could not be loaded ({@link Kind#KEY_UNREADABLE}), if shown
     */
    public record SshFailure(Kind kind, String host, String fingerprint, String knownHosts, String keyFile,
                             String reason) {
    }

    private static final Pattern CHANGED = Pattern.compile("REMOTE HOST IDENTIFICATION HAS CHANGED"
            + "|Host key for \\S+ has changed");
    private static final Pattern CHANGED_HOST = Pattern.compile("Host key for (\\S+) has changed");
    private static final Pattern FINGERPRINT = Pattern.compile("sent by the remote host is\\s+(\\S+?)\\.?\\s*$",
            Pattern.MULTILINE);
    private static final Pattern OFFENDING = Pattern.compile("Offending \\S+ key in (.+:\\d+)");
    private static final Pattern NOT_KNOWN = Pattern.compile("No \\S+ host key is known for (\\S+?)\\s+and");
    private static final Pattern VERIFICATION = Pattern.compile("Host key verification failed");
    private static final Pattern MISSING = Pattern.compile(
            "Identity file (.+?) not accessible: No such file or directory");
    private static final Pattern LOAD_KEY = Pattern.compile("Load key \"(.+?)\": (.+)");
    private static final Pattern UNPROTECTED = Pattern.compile("UNPROTECTED PRIVATE KEY FILE");
    private static final Pattern BAD_PERMISSIONS = Pattern.compile("Permissions \\S+ for '(.+?)' are too open");
    private static final Pattern REJECTED = Pattern.compile("(?:\\S+@)?([^\\s@:]+): Permission denied \\(publickey");

    private SshFailures() {
    }

    /** The SSH problem {@code stderr} reports, if any. */
    public static Optional<SshFailure> find(String stderr) {
        if (stderr == null || stderr.isBlank()) {
            return Optional.empty();
        }
        if (CHANGED.matcher(stderr).find()) {
            return Optional.of(new SshFailure(Kind.HOST_KEY_CHANGED, group(CHANGED_HOST, stderr),
                    group(FINGERPRINT, stderr), group(OFFENDING, stderr), null, null));
        }
        if (VERIFICATION.matcher(stderr).find() || NOT_KNOWN.matcher(stderr).find()) {
            return Optional.of(new SshFailure(Kind.HOST_KEY_UNKNOWN, group(NOT_KNOWN, stderr), null, null, null,
                    null));
        }
        String missing = group(MISSING, stderr);
        if (missing != null) {
            return Optional.of(new SshFailure(Kind.KEY_FILE_MISSING, group(REJECTED, stderr), null, null,
                    missing, null));
        }
        Matcher load = LOAD_KEY.matcher(stderr);
        if (load.find()) {
            return Optional.of(new SshFailure(Kind.KEY_UNREADABLE, group(REJECTED, stderr), null, null,
                    load.group(1), load.group(2).strip()));
        }
        if (UNPROTECTED.matcher(stderr).find()) {
            return Optional.of(new SshFailure(Kind.KEY_UNREADABLE, group(REJECTED, stderr), null, null,
                    group(BAD_PERMISSIONS, stderr), "bad permissions"));
        }
        String rejected = group(REJECTED, stderr);
        if (rejected != null) {
            return Optional.of(new SshFailure(Kind.KEY_REJECTED, rejected, null, null, null, null));
        }
        return Optional.empty();
    }

    private static String group(Pattern pattern, String text) {
        Matcher m = pattern.matcher(text);
        return m.find() ? m.group(1).strip() : null;
    }
}
