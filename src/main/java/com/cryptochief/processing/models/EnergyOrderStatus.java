package com.cryptochief.processing.models;

public final class EnergyOrderStatus {
    /** The key is claimed, no supplier has been called yet. Safe to abandon, safe to retry. */
    public static final String RESERVED = "reserved";
    /** A supplier accepted the order and named it; the energy is being waited for on chain. */
    public static final String PLACED = "placed";
    /** The energy has been delegated to the receive address. */
    public static final String DELIVERED = "delivered";
    /** The energy could not be bought; nobody was charged, retrying with the same parameters will not help. */
    public static final String REFUSED = "refused";
    /** The outcome is not known yet - the order is being checked with the supplier. Do not retry. */
    public static final String UNRESOLVED = "unresolved";
    /** The customer's money went back. Terminal. */
    public static final String REFUNDED = "refunded";

    private EnergyOrderStatus() {}
}
