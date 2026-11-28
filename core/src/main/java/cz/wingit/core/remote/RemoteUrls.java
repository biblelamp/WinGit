package cz.wingit.core.remote;

import java.util.Locale;

/**
 * How Git talks to a remote, judged from its URL as Git does: which credentials it needs.
 */
public final class RemoteUrls {

    /** The ways of connecting that matter for signing in. */
    public enum Transport {
        /** {@code ssh://...} or {@code user@host:path}: SSH key. */
        SSH,
        /** {@code https://...} (and {@code http://}): user name and password or token. */
        HTTPS,
        /** A folder or {@code file://}: no sign-in. */
        LOCAL,
        /** Anything else ({@code git://}, helpers such as {@code foo::...}). */
        OTHER
    }

    private RemoteUrls() {
    }

    public static Transport transport(String url) {
        if (url == null || url.isBlank()) {
            return Transport.OTHER;
        }
        String text = url.strip();
        String lower = text.toLowerCase(Locale.ROOT);
        int scheme = lower.indexOf("://");
        if (scheme > 0) {
            String name = lower.substring(0, scheme);
            if (name.equals("ssh") || name.equals("git+ssh") || name.equals("ssh+git")) {
                return Transport.SSH;
            }
            if (name.equals("https") || name.equals("http")) {
                return Transport.HTTPS;
            }
            return name.equals("file") ? Transport.LOCAL : Transport.OTHER;
        }
        if (text.contains("::")) {
            return Transport.OTHER; // remote helper
        }
        // A Windows drive (C:\x, C:/x) is a path; otherwise "host:path" before any slash is scp-like SSH.
        if (text.length() >= 2 && Character.isLetter(text.charAt(0)) && text.charAt(1) == ':') {
            return Transport.LOCAL;
        }
        int colon = text.indexOf(':');
        int slash = text.indexOf('/');
        if (colon > 0 && (slash < 0 || colon < slash)) {
            return Transport.SSH;
        }
        return Transport.LOCAL;
    }
}
