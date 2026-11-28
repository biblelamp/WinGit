package cz.wingit.ui.settings;

import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;

/**
 * Everything the Settings dialog edits: the appearance (applied at the next start), the automatic
 * fetch interval and the SSH key (both applied at once).
 *
 * @param sshKey private key for SSH remotes of repositories without their own; empty: ssh's defaults
 */
public record Preferences(Appearance appearance, FetchInterval fetch, Optional<Path> sshKey) {

    public static final Preferences DEFAULT = new Preferences(Appearance.DEFAULT, FetchInterval.DEFAULT,
            Optional.empty());

    public Preferences {
        Objects.requireNonNull(appearance, "appearance");
        Objects.requireNonNull(fetch, "fetch");
        Objects.requireNonNull(sshKey, "sshKey");
    }
}
