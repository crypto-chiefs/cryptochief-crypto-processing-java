package com.cryptochief.processing.models;

import com.cryptochief.processing.Chain;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record PayoutInfo(
        @JsonProperty("uuid") String uuid,
        @JsonProperty("order_id") String orderId,
        @JsonProperty("status") String status,
        @JsonProperty("network") Chain network,
        @JsonProperty("coin") String coin,
        @JsonProperty("amount") String amount,
        @JsonProperty("to_address") String toAddress,
        @JsonProperty("txid") String txid,
        @JsonProperty("sources") List<PayoutSource> sources,
        @JsonProperty("url_callback") String urlCallback,
        @JsonProperty("created_at") String createdAt,
        @JsonProperty("updated_at") String updatedAt,
        @JsonProperty("error") String error
) {
    public boolean isTerminal() {
        return PayoutStatus.TERMINAL.contains(status);
    }

    public boolean succeeded() {
        return PayoutStatus.PAID.equals(status);
    }
}
