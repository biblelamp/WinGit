package cz.wingit.core;

import java.util.Objects;

/**
 * The Git user that commits in one repository use, and which of its values the repository sets itself
 * ({@code git config --local}) instead of taking them from the global or system configuration.
 */
public record RepositoryIdentity(GitIdentity identity, boolean ownName, boolean ownEmail) {

    public RepositoryIdentity {
        Objects.requireNonNull(identity, "identity");
    }
}
