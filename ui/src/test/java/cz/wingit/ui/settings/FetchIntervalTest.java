package cz.wingit.ui.settings;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;

import org.junit.jupiter.api.Test;

class FetchIntervalTest {

    @Test
    void durationIsEmptyWhenSwitchedOffButMinutesAreKept() {
        assertThat(new FetchInterval(true, 15).duration()).contains(Duration.ofMinutes(15));
        assertThat(new FetchInterval(false, 15).duration()).isEmpty();
        assertThat(new FetchInterval(false, 15).minutes()).isEqualTo(15);
    }

    @Test
    void minutesStayInRange() {
        assertThat(FetchInterval.clamp(0)).isEqualTo(FetchInterval.MIN_MINUTES);
        assertThat(FetchInterval.clamp(5000)).isEqualTo(FetchInterval.MAX_MINUTES);
        assertThatThrownBy(() -> new FetchInterval(true, 0)).isInstanceOf(IllegalArgumentException.class);
    }
}
