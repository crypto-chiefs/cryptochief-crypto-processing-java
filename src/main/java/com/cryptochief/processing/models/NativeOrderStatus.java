package com.cryptochief.processing.models;

public final class NativeOrderStatus {
    /** The key is claimed, the transfer has not been sent yet. Safe to abandon, safe to retry. */
    public static final String RESERVED = "reserved";
    /** The coins have been sent to the receive address; {@code tx_hash} carries the transfer. */
    public static final String DELIVERED = "delivered";
    /** The coins could not be bought; nobody was charged, retrying with the same parameters will not help. */
    public static final String REFUSED = "refused";
    /** The outcome is not known yet - the order is being checked. Do not retry. */
    public static final String UNRESOLVED = "unresolved";

    private NativeOrderStatus() {}
}
