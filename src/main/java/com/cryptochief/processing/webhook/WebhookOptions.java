package com.cryptochief.processing.webhook;

import java.time.Clock;
import java.time.Duration;
import java.util.Objects;

/** Tolerance and clock of {@link WebhookVerifier}. Immutable. */
public final class WebhookOptions {

    private static final WebhookOptions DEFAULTS =
            new WebhookOptions(WebhookVerifier.DEFAULT_TOLERANCE, Clock.systemUTC());

    private final Duration tolerance;
    private final Clock clock;

    private WebhookOptions(Duration tolerance, Clock clock) {
        this.tolerance = tolerance;
        this.clock = clock;
    }

    /** Tolerance {@link WebhookVerifier#DEFAULT_TOLERANCE}, system UTC clock. */
    public static WebhookOptions defaults() {
        return DEFAULTS;
    }

    /**
     * Maximum difference between {@code X-CC-Timestamp} and the clock, compared in whole seconds.
     *
     * @throws IllegalArgumentException {@code tolerance} is zero or negative
     */
    public WebhookOptions withTolerance(Duration tolerance) {
        Objects.requireNonNull(tolerance, "tolerance");
        if (tolerance.isZero() || tolerance.isNegative()) {
            throw new IllegalArgumentException("tolerance must be positive");
        }
        return new WebhookOptions(tolerance, clock);
    }

    /** Source of the current time. */
    public WebhookOptions withClock(Clock clock) {
        return new WebhookOptions(tolerance, Objects.requireNonNull(clock, "clock"));
    }

    public Duration tolerance() {
        return tolerance;
    }

    public Clock clock() {
        return clock;
    }
}
