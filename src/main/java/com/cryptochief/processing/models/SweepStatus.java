package com.cryptochief.processing.models;

/**
 * Sweep status values.
 *
 * <p>A sweep is broadcast first and confirmed after: {@link #BROADCASTED} means the
 * transaction is out and not yet confirmed, {@link #COMPLETED} means the chain confirmed
 * it. The platform used to report {@code completed} at broadcast, so a sweep could read as
 * settled while its transaction was still unconfirmed or had been dropped.
 *
 * <p>{@link #SKIPPED} is a sweep the platform decided against - almost always a balance
 * below the wallet's threshold. A normal outcome, not a failure.
 */
public final class SweepStatus {

    public static final String PENDING = "pending";
    public static final String WAITING_GAS = "waiting_gas";
    public static final String BROADCASTED = "broadcasted";
    public static final String COMPLETED = "completed";
    public static final String FAILED = "failed";
    public static final String SKIPPED = "skipped";

    private SweepStatus() {}
}
