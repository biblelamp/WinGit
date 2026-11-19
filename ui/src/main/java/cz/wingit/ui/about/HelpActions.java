package cz.wingit.ui.about;

import java.awt.Component;
import java.awt.event.ActionEvent;
import java.util.function.Supplier;

import javax.swing.AbstractAction;
import javax.swing.Action;

import cz.wingit.core.GitService;
import cz.wingit.ui.Messages;

/**
 * Actions of the Help menu.
 */
public final class HelpActions {

    private HelpActions() {
    }

    /** Opens the project website in the browser. */
    public static Action website(Component parent) {
        return link(parent, "menu.help.website", Links.WEBSITE);
    }

    /** Opens a new GitHub issue in the browser. */
    public static Action reportIssue(Component parent) {
        return link(parent, "menu.help.reportIssue", Links.NEW_ISSUE);
    }

    /** Shows the About window; {@code appVersion} supplies the version ({@code null} = development build). */
    public static Action about(Component parent, GitService git, Supplier<String> appVersion) {
        return new AbstractAction(Messages.get("menu.help.about")) {
            private static final long serialVersionUID = 1L;

            @Override
            public void actionPerformed(ActionEvent e) {
                AboutDialog.show(parent, git, appVersion.get());
            }
        };
    }

    private static Action link(Component parent, String key, String url) {
        return new AbstractAction(Messages.get(key)) {
            private static final long serialVersionUID = 1L;

            @Override
            public void actionPerformed(ActionEvent e) {
                Links.open(parent, url);
            }
        };
    }
}
