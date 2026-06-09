package com.cryptochief.processing;

import java.time.Duration;

/** Tuning for the {@code waitFor*} polling helpers. */
public record PollOptions(Duration interval, Duration timeout) {

    public static PollOptions defaults() {
        return new PollOptions(Duration.ofSeconds(5), Duration.ofMinutes(10));
    }
}
