package cz.wingit.ui.about;

import cz.wingit.ui.Messages;

/**
 * What the About window shows and copies for bug reports.
 *
 * @param appVersion WinGit version, or {@code null} for a development build
 * @param java       Java version and vendor
 * @param os         operating system name, version and architecture
 * @param git        Git version, or {@code null} while unknown
 */
record AboutInfo(String appVersion, String java, String os, String git) {

    /** This machine's Java and OS; Git still unknown (reading it starts a process). */
    static AboutInfo current(String appVersion) {
        return new AboutInfo(appVersion,
                System.getProperty("java.version") + " (" + System.getProperty("java.vendor") + ")",
                System.getProperty("os.name") + " " + System.getProperty("os.version")
                        + " (" + System.getProperty("os.arch") + ")",
                null);
    }

    AboutInfo withGit(String gitVersion) {
        return new AboutInfo(appVersion, java, os, gitVersion);
    }

    /** E.g. {@code 0.3.3}, or "development build". */
    String versionText() {
        return appVersion == null || appVersion.isBlank() ? Messages.get("about.versionUnknown") : appVersion;
    }

    /** Plain text for the clipboard; labels stay English so any reader of a bug report understands them. */
    String details() {
        return "WinGit " + versionText() + "\n"
                + "Java: " + java + "\n"
                + "Git: " + (git != null ? git : "unknown") + "\n"
                + "OS: " + os + "\n";
    }
}
