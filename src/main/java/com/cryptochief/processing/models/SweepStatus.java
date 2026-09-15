package com.cryptochief.processing.models;

/**
 * Sweep status values.
 *
 * <p>{@link #BROADCASTED}: sent, {@code sweepConfirmations} growing. {@link #COMPLETED}:
 * reached {@code requiredConfirmations}.
 *
 * <p>{@link #SKIPPED} is a sweep the platform decided against - almost always a balance
 * below the wallet's threshold. A normal outcome, not a failure.
 *
 * <p>{@link #COMPLETED}, {@link #FAILED} and {@link #SKIPPED} are the terminal outcomes. Settled:
 * {@link #COMPLETED} and {@code sweepConfirmations} above zero, or the {@code sweep.confirmed}
 * webhook. On older records {@link #COMPLETED} can have {@code 0} and is then not settled.
 * {@code Sweep.completedAt()} is the send time (for waiting_gas, failed and skipped, the time
 * that status was recorded), not a settlement signal.
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
