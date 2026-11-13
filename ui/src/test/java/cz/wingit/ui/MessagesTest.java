package cz.wingit.ui;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class MessagesTest {

    @Test
    void mainWindowTitleIsDefined() {
        assertThat(Messages.get("mainWindow.title")).isEqualTo("WinGit");
    }
}
