package com.cryptochief.processing.models;

import java.util.Set;

/** Status values of a manual withdrawal. */
public final class WithdrawalStatus {

    /** Accepted, waiting to be processed. */
    public static final String QUEUE = "queue";

    /** The source wallet is being topped up with native coin for gas. */
    public static final String REFUELING = "refueling";

    /** Gas is in place or was not needed. */
    public static final String REFUEL_CONFIRMED = "refuel_confirmed";

    /** The transaction is being built, signed and sent. */
    public static final String SENDING = "sending";

    /** EVM: queued for broadcast. */
    public static final String BROADCASTING = "broadcasting";

    /** Bitcoin family: in the mempool, not yet in a block. */
    public static final String IN_MEMPOOL = "in_mempool";

    /** Sent, waiting for {@code requiredConfirmations}. */
    public static final String CONFIRM_CHECK = "confirm_check";

    /** Reached {@code requiredConfirmations}. */
    public static final String COMPLETED = "completed";

    /** Did not go through; {@code error_reason} says why. */
    public static final String FAILED = "failed";

    /**
     * Not produced by the API.
     *
     * @deprecated Not produced by the API.
     */
    @Deprecated
    public static final String CANCELLED = "cancelled";

    /** Terminal statuses. */
    public static final Set<String> TERMINAL = Set.of(COMPLETED, FAILED, CANCELLED);

    private WithdrawalStatus() {}
}
