package cz.wingit.core.repo;

/**
 * Helpers for clone URLs.
 */
public final class CloneUrls {

    private CloneUrls() {
    }

    /**
     * The folder name Git would pick for a clone of {@code url}, e.g.
     * {@code https://host/team/app.git -> app}, {@code git@host:team/app -> app}.
     * Returns an empty string if no name can be derived.
     */
    public static String directoryName(String url) {
        String s = stripTrailingSeparators(url == null ? "" : url.strip());
        if (s.endsWith(".git")) {
            s = stripTrailingSeparators(s.substring(0, s.length() - ".git".length()));
        }
        int start = Math.max(s.lastIndexOf('/'), Math.max(s.lastIndexOf('\\'), s.lastIndexOf(':')));
        return s.substring(start + 1);
    }

    private static String stripTrailingSeparators(String s) {
        int end = s.length();
        while (end > 0 && (s.charAt(end - 1) == '/' || s.charAt(end - 1) == '\\')) {
            end--;
        }
        return s.substring(0, end);
    }
}
