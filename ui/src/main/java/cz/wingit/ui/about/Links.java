package cz.wingit.ui.about;

import java.awt.Component;
import java.awt.Desktop;
import java.net.URI;

import javax.swing.SwingUtilities;

import cz.wingit.ui.Messages;
import cz.wingit.ui.common.Dialogs;

/**
 * Project web addresses and opening them in the default browser.
 */
public final class Links {

    public static final String WEBSITE = "https://wgit.cz/";
    public static final String SOURCE = "https://github.com/biblelamp/WinGit";
    public static final String NEW_ISSUE = SOURCE + "/issues/new";

    private Links() {
    }

    /**
     * Third-party notices of this version: the file at the release tag, or on {@code main} for a
     * development build (unknown or {@code -SNAPSHOT} version).
     */
    static String thirdPartyNotices(String appVersion) {
        boolean release = appVersion != null && !appVersion.isBlank() && !appVersion.contains("SNAPSHOT");
        String ref = release ? "v" + appVersion.strip() : "main";
        return SOURCE + "/blob/" + ref + "/THIRD-PARTY-NOTICES.md";
    }

    /** {@code https://wgit.cz/} -> {@code wgit.cz}: an address as people read it. */
    static String display(String url) {
        String text = url.replaceFirst("^https?://", "");
        return text.endsWith("/") ? text.substring(0, text.length() - 1) : text;
    }

    /**
     * Opens {@code url} in the default browser, off the EDT (it can take a moment); if that is not
     * possible, shows the address so it can be copied. Call on the EDT.
     */
    public static void open(Component parent, String url) {
        Thread opener = new Thread(() -> {
            try {
                if (!Desktop.isDesktopSupported() || !Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                    throw new UnsupportedOperationException("no browser");
                }
                Desktop.getDesktop().browse(new URI(url));
            } catch (Exception e) {
                SwingUtilities.invokeLater(() -> Dialogs.showError(parent, Messages.get("links.error.title"),
                        Messages.format("links.error.message", url)));
            }
        }, "open-link");
        opener.setDaemon(true);
        opener.start();
    }
}
