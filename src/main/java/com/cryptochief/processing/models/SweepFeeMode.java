package com.cryptochief.processing.models;

/**
 * Who pays the gas for a sweep: {@link #CLIENT} the swept wallet itself, {@link #SERVICE}
 * the platform's service wallet, {@link #MIX} the service wallet with the cost reclaimed
 * from the sweep.
 */
public final class SweepFeeMode {

    public static final String CLIENT = "client";
    public static final String SERVICE = "service";
    public static final String MIX = "mix";

    private SweepFeeMode() {}
}
