package com.cryptochief.processing.models;

import com.cryptochief.processing.Chain;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record SignTransactionResponse(
        @JsonProperty("uuid") String uuid,
        @JsonProperty("status") String status,
        @JsonProperty("signed_tx_hex") String signedTxHex,
        @JsonProperty("tx_hash") String txHash,
        @JsonProperty("expires_at") String expiresAt,
        @JsonProperty("chain_family") String chainFamily,
        @JsonProperty("network") Chain network
) {}
