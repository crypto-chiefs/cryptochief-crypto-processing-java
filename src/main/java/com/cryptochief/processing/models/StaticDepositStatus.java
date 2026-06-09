package com.cryptochief.processing.models;

public final class StaticDepositStatus {
    public static final String IN_MEMPOOL = "in_mempool";
    public static final String CONFIRM_CHECK = "confirm_check";
    public static final String PAID = "paid";
    public static final String DROPPED = "dropped";
    public static final String REORGED = "reorged";

    private StaticDepositStatus() {}
}
