package com.cryptochief.processing.services;

import com.cryptochief.processing.http.HttpTransport;
import com.cryptochief.processing.models.CreatePayInRequest;
import com.cryptochief.processing.models.HistoryQuery;
import com.cryptochief.processing.models.PayIn;
import com.cryptochief.processing.models.PayInHistoryResponse;
import com.cryptochief.processing.models.SelectAssetRequest;
import com.cryptochief.processing.models.UuidRequest;

/** Incoming-payment endpoints. */
public final class PayInsService {

    private final HttpTransport transport;

    public PayInsService(HttpTransport transport) {
        this.transport = transport;
    }

    public PayIn create(CreatePayInRequest request) {
        return transport.send("/v1/payments/order/create", request, PayIn.class);
    }

    public PayIn selectAsset(SelectAssetRequest request) {
        return transport.send("/v1/payments/asset/select", request, PayIn.class);
    }

    public PayIn resetAsset(String uuid) {
        return transport.send("/v1/payments/asset/reset", new UuidRequest(uuid), PayIn.class);
    }

    public PayIn cancel(String uuid) {
        return transport.send("/v1/payments/order/cancel", new UuidRequest(uuid), PayIn.class);
    }

    public PayIn info(String uuid) {
        return transport.send("/v1/payments/order/info", new UuidRequest(uuid), PayIn.class);
    }

    public PayInHistoryResponse history(HistoryQuery query) {
        return transport.send("/v1/payments/history", query, PayInHistoryResponse.class);
    }

    public PayInHistoryResponse history() {
        return history(HistoryQuery.empty());
    }
}
