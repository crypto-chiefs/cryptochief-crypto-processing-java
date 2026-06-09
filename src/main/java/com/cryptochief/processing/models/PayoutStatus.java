package com.cryptochief.processing.models;

import java.util.Set;

public final class PayoutStatus {
    public static final String QUEUE = "queue";
    public static final String PROCESS = "process";
    public static final String PAID = "paid";
    public static final String FAILED = "failed";
    public static final String SYSTEM_FAIL = "system_fail";
    public static final String EXPIRED = "expired";
    public static final String CANCEL = "cancel";

    public static final Set<String> TERMINAL = Set.of(PAID, FAILED, SYSTEM_FAIL, EXPIRED, CANCEL);

    private PayoutStatus() {}
}
