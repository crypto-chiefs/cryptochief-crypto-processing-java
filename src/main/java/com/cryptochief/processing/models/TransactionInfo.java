package com.cryptochief.processing.models;

import com.cryptochief.processing.Chain;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record TransactionInfo(
        @JsonProperty("uuid") String uuid,
        @JsonProperty("status") String status,
        @JsonProperty("network") Chain network,
        @JsonProperty("chain_family") String chainFamily,
        @JsonProperty("from_address") String fromAddress,
        @JsonProperty("to_address") String toAddress,
        @JsonProperty("type") String type,
        @JsonProperty("value") String value,
        @JsonProperty("coin") String coin,
        @JsonProperty("contract") String contract,
        @JsonProperty("tx_hash") String txHash,
        @JsonProperty("signed_tx_hex") String signedTxHex,
        @JsonProperty("expires_at") String expiresAt,
        @JsonProperty("nonce") Long nonce,
        @JsonProperty("actual_fee") String actualFee,
        @JsonProperty("actual_fee_fiat") String actualFeeFiat,
        @JsonProperty("created_at") String createdAt,
        @JsonProperty("updated_at") String updatedAt,
        @JsonProperty("error") String error
) {
    public boolean isTerminal() {
        return TxStatus.TERMINAL.contains(status);
    }

    public boolean succeeded() {
        return TxStatus.CONFIRMED.equals(status);
    }
}
