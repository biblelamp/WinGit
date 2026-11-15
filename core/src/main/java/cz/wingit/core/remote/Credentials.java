package cz.wingit.core.remote;

import java.util.Objects;

/**
 * User name and password (or personal access token) for an HTTPS remote, entered by the user
 * for one operation. Never persisted by WinGit; see {@code GitService#storeCredentials}.
 */
public record Credentials(String username, String password) {

    public Credentials {
        Objects.requireNonNull(username, "username");
        Objects.requireNonNull(password, "password");
    }

    /** Hides the password, e.g. in logs and exception messages. */
    @Override
    public String toString() {
        return "Credentials[username=" + username + ", password=***]";
    }
}
