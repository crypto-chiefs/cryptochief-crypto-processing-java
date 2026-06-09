package com.cryptochief.processing.services;

import com.cryptochief.processing.Chain;
import com.cryptochief.processing.http.HttpTransport;
import com.cryptochief.processing.models.ForceSweepRequest;
import com.cryptochief.processing.models.ForceSweepResponse;
import com.cryptochief.processing.models.SweepHistoryQuery;
import com.cryptochief.processing.models.SweepHistoryResponse;
import com.cryptochief.processing.models.SweepWalletHistoryQuery;

/** Transit → master sweep endpoints. */
public final class SweepsService {

    private final HttpTransport transport;

    public SweepsService(HttpTransport transport) {
        this.transport = transport;
    }

    public ForceSweepResponse force(String address, Chain network) {
        return transport.send("/v1/sweeps/force",
                new ForceSweepRequest(address, network), ForceSweepResponse.class);
    }

    public SweepHistoryResponse history(SweepHistoryQuery query) {
        return transport.send("/v1/sweeps/history", query, SweepHistoryResponse.class);
    }

    public SweepHistoryResponse history() {
        return history(SweepHistoryQuery.empty());
    }

    public SweepHistoryResponse walletHistory(SweepWalletHistoryQuery query) {
        return transport.send("/v1/sweeps/wallet/history", query, SweepHistoryResponse.class);
    }
}
