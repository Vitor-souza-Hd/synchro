package org.example.synchro.services;

import java.time.DateTimeException;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeFormatter;

final class RetryAfter {
    private RetryAfter() {}

    static Instant deadline(String header, Instant now, Duration defaultDelay) {
        if (header == null) return now.plus(defaultDelay);
        String value = header.strip();
        try {
            long seconds = Long.parseLong(value);
            if (seconds >= 0) {
                long limit = Instant.MAX.getEpochSecond() - now.getEpochSecond() - 1;
                return now.plusSeconds(Math.max(1, Math.min(seconds, limit)));
            }
        } catch (NumberFormatException ignored) {
            try {
                Instant date = Instant.from(DateTimeFormatter.RFC_1123_DATE_TIME.parse(value));
                return date.isAfter(now) ? date : now.plusSeconds(1);
            } catch (DateTimeException invalidDate) {
                // Use a short local delay when the provider's header cannot be read.
            }
        }
        return now.plus(defaultDelay);
    }

    static long secondsRemaining(Instant now, Instant deadline) {
        Duration remaining = Duration.between(now, deadline);
        return Math.max(1, remaining.toSeconds() + (remaining.getNano() == 0 ? 0 : 1));
    }
}
