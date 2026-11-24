package cz.wingit.ui.settings;

import java.time.Duration;
import java.util.Optional;

/**
 * How often all repositories of the list are fetched in the background, as chosen in the Settings dialog.
 * The minutes are kept while the automatic fetch is switched off, so switching it on again restores them.
 *
 * @param enabled whether repositories are fetched automatically
 * @param minutes the interval, {@link #MIN_MINUTES}..{@link #MAX_MINUTES}
 */
public record FetchInterval(boolean enabled, int minutes) {

    public static final int MIN_MINUTES = 1;
    public static final int MAX_MINUTES = 1440;
    public static final FetchInterval DEFAULT = new FetchInterval(true, 10);

    public FetchInterval {
        if (minutes < MIN_MINUTES || minutes > MAX_MINUTES) {
            throw new IllegalArgumentException("minutes out of range: " + minutes);
        }
    }

    /** The interval, or empty when the automatic fetch is off. */
    public Optional<Duration> duration() {
        return enabled ? Optional.of(Duration.ofMinutes(minutes)) : Optional.empty();
    }

    /** Clamps stored or typed minutes into the allowed range. */
    public static int clamp(int minutes) {
        return Math.max(MIN_MINUTES, Math.min(MAX_MINUTES, minutes));
    }
}
