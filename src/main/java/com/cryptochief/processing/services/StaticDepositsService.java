package com.cryptochief.processing.services;

import com.cryptochief.processing.http.HttpTransport;
import com.cryptochief.processing.models.StaticDeposit;
import com.cryptochief.processing.models.StaticDepositHistoryQuery;
import com.cryptochief.processing.models.StaticDepositHistoryResponse;
import com.cryptochief.processing.models.UuidRequest;

/** Read endpoints for static (per-customer) deposits. */
public final class StaticDepositsService {

    private final HttpTransport transport;

    public StaticDepositsService(HttpTransport transport) {
        this.transport = transport;
    }

    public StaticDeposit info(String uuid) {
        return transport.send("/v1/static-deposit/info", new UuidRequest(uuid), StaticDeposit.class);
    }

    public StaticDepositHistoryResponse history(StaticDepositHistoryQuery query) {
        return transport.send("/v1/static-deposit/history", query, StaticDepositHistoryResponse.class);
    }

    public StaticDepositHistoryResponse history() {
        return history(StaticDepositHistoryQuery.empty());
    }
}
