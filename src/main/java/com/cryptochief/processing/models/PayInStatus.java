package com.cryptochief.processing.models;

import java.util.Set;

public final class PayInStatus {
    public static final String WAITING_ASSET_SELECT = "waiting_asset_select";
    public static final String PENDING = "pending";
    public static final String PROCESSING = "processing";
    public static final String PROCESS = "process";
    public static final String PAID = "paid";
    public static final String CANCEL = "cancel";
    public static final String EXPIRED = "expired";

    public static final Set<String> TERMINAL = Set.of(PAID, CANCEL, EXPIRED);

    private PayInStatus() {}
}
