package com.cryptochief.processing.models;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ExecuteTransactionRequest(
        @JsonProperty("uuid") String uuid,
        @JsonProperty("signed_tx_hex") String signedTxHex
) {
    public static ExecuteTransactionRequest of(String uuid) {
        return new ExecuteTransactionRequest(uuid, null);
    }
}
