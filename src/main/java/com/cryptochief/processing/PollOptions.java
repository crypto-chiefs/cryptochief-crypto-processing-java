package com.cryptochief.processing;

import java.time.Duration;

/** Tuning for the {@code waitFor*} polling helpers. */
public record PollOptions(Duration interval, Duration timeout) {

    /** 5 s interval, 10 min timeout. */
    public static PollOptions defaults() {
        return new PollOptions(Duration.ofSeconds(5), Duration.ofMinutes(10));
    }

    /** 5 s interval, 90 min timeout: the default of {@code waitForPayout}. */
    public static PollOptions payoutDefaults() {
        return new PollOptions(Duration.ofSeconds(5), Duration.ofMinutes(90));
    }
}
