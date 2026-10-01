package com.cryptochief.processing.models;

import java.util.Set;

public final class PayInStatus {
    public static final String WAITING_ASSET_SELECT = "waiting_asset_select";
    public static final String PENDING = "pending";
    public static final String PROCESSING = "processing";
    public static final String PROCESS = "process";
    public static final String PAID = "paid";
    /** Final: the payment landed short of the invoiced amount. */
    public static final String PAID_LESS = "paid_less";
    /** Final: the payment landed above the invoiced amount. */
    public static final String PAID_OVER = "paid_over";
    public static final String CANCEL = "cancel";
    public static final String EXPIRED = "expired";
    /** Partially paid multiple-payment order; the remainder can still arrive. Not terminal. */
    public static final String WRONG_AMOUNT_WAITING = "wrong_amount_waiting";

    public static final Set<String> TERMINAL = Set.of(PAID, PAID_LESS, PAID_OVER, CANCEL, EXPIRED);

    private PayInStatus() {}
}
