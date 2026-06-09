package com.cryptochief.processing.models;

import java.util.Set;

public final class TxStatus {
    public static final String SIGNED = "signed";
    public static final String BROADCASTING = "broadcasting";
    public static final String BROADCASTED = "broadcasted";
    public static final String CONFIRMED = "confirmed";
    public static final String FAILED = "failed";
    public static final String EXPIRED = "expired";

    public static final Set<String> TERMINAL = Set.of(CONFIRMED, FAILED, EXPIRED);

    private TxStatus() {}
}
