package com.cryptochief.processing.services;

import com.cryptochief.processing.http.HttpTransport;
import com.cryptochief.processing.models.BatchExecuteRequest;
import com.cryptochief.processing.models.BatchExecuteResponse;
import com.cryptochief.processing.models.EstimatePayoutRequest;
import com.cryptochief.processing.models.EstimatePayoutResponse;
import com.cryptochief.processing.models.ExecutePayoutRequest;
import com.cryptochief.processing.models.HistoryQuery;
import com.cryptochief.processing.models.PayoutHistoryResponse;
import com.cryptochief.processing.models.PayoutInfo;
import com.cryptochief.processing.models.UuidRequest;

/** Payout endpoints. Idempotency key is {@link ExecutePayoutRequest#orderId()}. */
public final class PayoutsService {

    private final HttpTransport transport;

    public PayoutsService(HttpTransport transport) {
        this.transport = transport;
    }

    public EstimatePayoutResponse estimate(EstimatePayoutRequest request) {
        return transport.send("/v1/payout/estimate", request, EstimatePayoutResponse.class);
    }

    public PayoutInfo execute(ExecutePayoutRequest request) {
        return transport.send("/v1/payout/execute", request, PayoutInfo.class);
    }

    public PayoutInfo info(String uuid) {
        return transport.send("/v1/payout/info", new UuidRequest(uuid), PayoutInfo.class);
    }

    public PayoutHistoryResponse history(HistoryQuery query) {
        return transport.send("/v1/payout/history", query, PayoutHistoryResponse.class);
    }

    public PayoutHistoryResponse history() {
        return history(HistoryQuery.empty());
    }

    public BatchExecuteResponse batchEstimate(BatchExecuteRequest request) {
        return transport.send("/v1/payout/batch/estimate", request, BatchExecuteResponse.class);
    }

    public BatchExecuteResponse batchExecute(BatchExecuteRequest request) {
        return transport.send("/v1/payout/batch/execute", request, BatchExecuteResponse.class);
    }
}
