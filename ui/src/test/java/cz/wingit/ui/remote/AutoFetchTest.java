package cz.wingit.ui.remote;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

import javax.swing.SwingUtilities;

import org.junit.jupiter.api.Test;

import cz.wingit.core.DefaultGitService;
import cz.wingit.core.cli.GitCli;

class AutoFetchTest {

    @Test
    void intervalCanBeChangedAndSwitchedOff() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            AutoFetch autoFetch = new AutoFetch(new DefaultGitService(new GitCli()), List::of, path -> { });
            assertThat(autoFetch.interval()).contains(AutoFetch.INTERVAL);

            autoFetch.setInterval(Optional.empty()); // before start: stays off when started
            autoFetch.start();
            assertThat(autoFetch.isRunning()).isFalse();
            assertThat(autoFetch.interval()).isEmpty();

            autoFetch.setInterval(Optional.of(Duration.ofMinutes(3)));
            assertThat(autoFetch.isRunning()).isTrue();
            assertThat(autoFetch.interval()).contains(Duration.ofMinutes(3));

            autoFetch.setInterval(Optional.empty());
            assertThat(autoFetch.isRunning()).isFalse();
        });
    }
}
