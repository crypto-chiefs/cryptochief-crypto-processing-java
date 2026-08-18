package com.cryptochief.processing.services;

import com.cryptochief.processing.http.HttpTransport;
import com.cryptochief.processing.models.CreditsBalance;
import com.cryptochief.processing.models.CreditsTopup;
import com.cryptochief.processing.models.CreditsTopupRequest;

import java.util.Map;

/** Credits endpoints (billing-exempt — checking the balance or topping up never spends a paid call). */
public final class CreditsService {

    private final HttpTransport transport;

    public CreditsService(HttpTransport transport) {
        this.transport = transport;
    }

    public CreditsBalance balance() {
        return transport.send("/v1/credits/balance", Map.of(), CreditsBalance.class);
    }

    public CreditsTopup topup(CreditsTopupRequest request) {
        return transport.send("/v1/credits/topup", request, CreditsTopup.class);
    }
}
