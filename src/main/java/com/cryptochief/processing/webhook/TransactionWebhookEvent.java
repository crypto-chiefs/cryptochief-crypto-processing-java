package com.cryptochief.processing.webhook;

import com.cryptochief.processing.Chain;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * {@code transaction.confirmed}, {@code transaction.failed} or {@code transaction.expired}:
 * the fields {@code client.transactions().info()} returns, plus {@code event}. Only final
 * statuses are sent. {@code confirmations} and {@code requiredConfirmations} are always present.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record TransactionWebhookEvent(
        @JsonProperty("event") String event,
        @JsonProperty("uuid") String uuid,
        @JsonProperty("status") String status,
        @JsonProperty("network") Chain network,
        @JsonProperty("chain_family") String chainFamily,
        @JsonProperty("type") String type,
        @JsonProperty("from_address") String fromAddress,
        @JsonProperty("to_address") String toAddress,
        @JsonProperty("value") String value,
        @JsonProperty("contract") String contract,
        @JsonProperty("tx_hash") String txHash,
        @JsonProperty("confirmations") int confirmations,
        @JsonProperty("required_confirmations") int requiredConfirmations,
        @JsonProperty("created_at") String createdAt,
        @JsonProperty("completed_at") String completedAt,
        @JsonProperty("error_reason") String errorReason
) {
    /** The 0.8.0 constructor; {@code confirmations} and {@code requiredConfirmations} are {@code 0}. */
    public TransactionWebhookEvent(String event, String uuid, String status, Chain network,
                                   String chainFamily, String type, String fromAddress,
                                   String toAddress, String value, String contract, String txHash,
                                   String createdAt, String completedAt, String errorReason) {
        this(event, uuid, status, network, chainFamily, type, fromAddress, toAddress, value,
                contract, txHash, 0, 0, createdAt, completedAt, errorReason);
    }
}
