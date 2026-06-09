package com.cryptochief.processing.http;

import java.time.Duration;
import java.util.concurrent.ThreadLocalRandom;

/** Exponential backoff with full jitter, capped at {@code max}. */
final class Backoff {

    private Backoff() {}

    static Duration delay(int attempt, Duration base, Duration max) {
        Duration safeBase = (base == null || base.isNegative() || base.isZero())
                ? Duration.ofMillis(200) : base;
        Duration safeMax = (max == null || max.isNegative() || max.isZero())
                ? Duration.ofSeconds(5) : max;
        int shift = Math.min(Math.max(attempt - 1, 0), 30);
        Duration shifted;
        try {
            shifted = safeBase.multipliedBy(1L << shift);
        } catch (ArithmeticException e) {
            shifted = safeMax;
        }
        Duration ceiling = (shifted.compareTo(safeMax) > 0 || shifted.isNegative()) ? safeMax : shifted;
        long ceilingMs = Math.max(0, ceiling.toMillis());
        long sampled = ThreadLocalRandom.current().nextLong(0, ceilingMs + 1);
        return Duration.ofMillis(sampled);
    }
}
