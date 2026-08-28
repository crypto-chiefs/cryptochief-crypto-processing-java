package com.cryptochief.processing.models;

/**
 * Auto-sweep modes.
 *
 * <ul>
 *   <li>{@link #OFF} - never swept on its own. A force sweep still works.</li>
 *   <li>{@link #MOMENTUM} - swept as soon as funds arrive.</li>
 *   <li>{@link #THRESHOLD} - swept once the balance reaches the threshold. A held balance
 *       is re-checked periodically, so a wallet that crosses the threshold through price
 *       movement alone is still swept.</li>
 * </ul>
 */
public final class SweepPolicyMode {

    public static final String OFF = "turned_off";
    public static final String MOMENTUM = "momentum";
    public static final String THRESHOLD = "threshold";

    private SweepPolicyMode() {}
}
