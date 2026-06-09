package com.cryptochief.processing.services;

import com.cryptochief.processing.http.HttpTransport;
import com.cryptochief.processing.models.HistoryQuery;
import com.cryptochief.processing.models.UuidRequest;
import com.cryptochief.processing.models.Withdrawal;
import com.cryptochief.processing.models.WithdrawalHistoryResponse;

/** Read-only withdrawal endpoints. */
public final class WithdrawalsService {

    private final HttpTransport transport;

    public WithdrawalsService(HttpTransport transport) {
        this.transport = transport;
    }

    public Withdrawal info(String uuid) {
        return transport.send("/v1/withdrawal/info", new UuidRequest(uuid), Withdrawal.class);
    }

    public WithdrawalHistoryResponse history(HistoryQuery query) {
        return transport.send("/v1/withdrawal/history", query, WithdrawalHistoryResponse.class);
    }

    public WithdrawalHistoryResponse history() {
        return history(HistoryQuery.empty());
    }
}
