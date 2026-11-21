package cz.wingit.ui.common;

import java.util.Locale;

import com.formdev.flatlaf.extras.FlatSVGIcon;

import cz.wingit.core.status.ChangeType;

/**
 * SVG icons bundled in {@code cz/wingit/ui/icons}. Icons use the IntelliJ "Actions.*" colors
 * (e.g. #6E6E6E, #389FD6), which FlatSVGIcon maps to the current theme; disabled variants
 * are derived automatically.
 */
public final class Icons {

    public static final int TOOLBAR_SIZE = 20;
    public static final int LIST_SIZE = 16;

    private static final String BASE = "cz/wingit/ui/icons/";

    private Icons() {
    }

    /** Toolbar icon {@code icons/toolbar/<name>.svg}. */
    public static FlatSVGIcon toolbar(String name) {
        return new FlatSVGIcon(BASE + "toolbar/" + name + ".svg", TOOLBAR_SIZE, TOOLBAR_SIZE);
    }

    /** File status icon {@code icons/files/<type>.svg}, e.g. {@code modified.svg}. */
    public static FlatSVGIcon fileStatus(ChangeType type) {
        // A rename has no icon of its own yet: the file was changed, so it shares the "modified" one.
        ChangeType shown = type == ChangeType.RENAMED ? ChangeType.MODIFIED : type;
        String name = shown.name().toLowerCase(Locale.ROOT);
        return new FlatSVGIcon(BASE + "files/" + name + ".svg", LIST_SIZE, LIST_SIZE);
    }

    /** Repository list icon: red variant when the working tree has changes. */
    public static FlatSVGIcon repository(boolean changed) {
        String name = changed ? "repository-changed" : "repository";
        return new FlatSVGIcon(BASE + "repositories/" + name + ".svg", LIST_SIZE, LIST_SIZE);
    }

    /** Repository list icon, green: commits were fetched that the current branch has not pulled yet. */
    public static FlatSVGIcon repositoryIncoming() {
        return new FlatSVGIcon(BASE + "repositories/repository-incoming.svg", LIST_SIZE, LIST_SIZE);
    }

    /** Small icon of the Diff header, {@code icons/diff/<name>.svg}, e.g. {@code next-change}. */
    public static FlatSVGIcon diff(String name) {
        return new FlatSVGIcon(BASE + "diff/" + name + ".svg", LIST_SIZE, LIST_SIZE);
    }

    /** Repository list icon for a repository whose folder no longer exists. */
    public static FlatSVGIcon repositoryMissing() {
        return new FlatSVGIcon(BASE + "repositories/repository-missing.svg", LIST_SIZE, LIST_SIZE);
    }
}
