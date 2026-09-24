package com.cryptochief.processing.models;

import java.util.Set;

public final class TxStatus {
    public static final String SIGNED = "signed";
    public static final String BROADCASTING = "broadcasting";
    /** Sent; {@code confirmations} grows towards {@code requiredConfirmations}. */
    public static final String BROADCASTED = "broadcasted";
    public static final String CONFIRMED = "confirmed";
    public static final String FAILED = "failed";
    public static final String EXPIRED = "expired";
    /**
     * EVM: replaced by a newer signature from the same address before it was executed;
     * {@code errorReason} is {@code SUPERSEDED_BY:<new uuid>}.
     */
    public static final String CANCELLED = "cancelled";

    public static final Set<String> TERMINAL = Set.of(CONFIRMED, FAILED, EXPIRED, CANCELLED);

    private TxStatus() {}
}
