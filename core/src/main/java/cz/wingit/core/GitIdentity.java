package cz.wingit.core;

import java.util.Objects;

/**
 * Who Git records as author and committer ({@code user.name}, {@code user.email}); an empty string means
 * the value is not set.
 */
public record GitIdentity(String name, String email) {

    public static final GitIdentity NONE = new GitIdentity("", "");

    public GitIdentity {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(email, "email");
    }
}
