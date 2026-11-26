package cz.wingit.ui.history;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.Locale;

import cz.wingit.ui.Messages;

/**
 * Commit date text: "Today 14:05", "Yesterday 09:32", otherwise a short localized date and time.
 */
public final class CommitDates {

    private final Clock clock;
    private final DateTimeFormatter time;
    private final DateTimeFormatter dateTime;

    CommitDates(Clock clock, Locale locale) {
        this.clock = clock;
        this.time = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(locale);
        this.dateTime = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT).withLocale(locale);
    }

    public static CommitDates system() {
        return new CommitDates(Clock.systemDefaultZone(), Locale.getDefault(Locale.Category.FORMAT));
    }

    public String format(Instant instant) {
        ZonedDateTime when = instant.atZone(clock.getZone());
        LocalDate today = LocalDate.now(clock);
        LocalDate day = when.toLocalDate();
        if (day.equals(today)) {
            return Messages.format("history.date.today", time.format(when));
        }
        if (day.equals(today.minusDays(1))) {
            return Messages.format("history.date.yesterday", time.format(when));
        }
        return dateTime.format(when);
    }
}
