package cz.wingit.ui.remote;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import cz.wingit.ui.Messages;

class LoginDialogTest {

    @Test
    void readsUserNameFromUrl() {
        assertThat(LoginDialog.usernameFromUrl("https://joe@github.com/a/b.git")).isEqualTo("joe");
        assertThat(LoginDialog.usernameFromUrl("https://joe:secret@github.com/a/b.git")).isEqualTo("joe");
        assertThat(LoginDialog.usernameFromUrl("https://github.com/a/b.git")).isEmpty();
        assertThat(LoginDialog.usernameFromUrl("not a url")).isEmpty();
    }

    @Test
    void displayUrlHidesUserInfo() {
        assertThat(LoginDialog.displayUrl("https://joe:secret@github.com/a/b.git"))
                .isEqualTo("https://github.com/a/b.git");
        assertThat(LoginDialog.displayUrl("https://dev.example:8443/r.git")).isEqualTo("https://dev.example:8443/r.git");
    }

    @Test
    void headerShowsUrl() {
        assertThat(Messages.format("login.header", "https://github.com/a/b.git"))
                .isEqualTo("Sign in to https://github.com/a/b.git");
    }
}
