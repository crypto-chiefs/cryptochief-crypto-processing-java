package com.cryptochief.processing.models;

import com.cryptochief.processing.Chain;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * A sign/execute transaction, as execute, info and history return it.
 *
 * <p>{@code confirmations} and {@code requiredConfirmations} are always present.
 * {@code confirmations} is {@code 0} until the transaction is in a block and grows while it is
 * {@link TxStatus#BROADCASTED}. At {@code requiredConfirmations} the transaction becomes
 * {@link TxStatus#CONFIRMED}.
 *
 * <p>{@code errorReason} says why the transaction is failed, expired or cancelled
 * ({@code SUPERSEDED_BY:<uuid>}), or why a signed one could not be executed yet
 * ({@code NONCE_GAP: missing_nonce=<n> blocking_uuid=<uuid>},
 * {@code NONCE_ALREADY_USED: chain_nonce=<n>}). {@code error} is not sent by the API.
 */
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
        @JsonProperty("confirmations") int confirmations,
        @JsonProperty("required_confirmations") int requiredConfirmations,
        @JsonProperty("signed_tx_hex") String signedTxHex,
        @JsonProperty("expires_at") String expiresAt,
        @JsonProperty("nonce") Long nonce,
        @JsonProperty("actual_fee") String actualFee,
        @JsonProperty("actual_fee_fiat") String actualFeeFiat,
        @JsonProperty("created_at") String createdAt,
        @JsonProperty("updated_at") String updatedAt,
        @JsonProperty("error") String error,
        @JsonProperty("error_reason") String errorReason
) {
    /** The 0.12.0 constructor; {@code errorReason} is {@code null}. */
    public TransactionInfo(String uuid, String status, Chain network, String chainFamily,
                           String fromAddress, String toAddress, String type, String value,
                           String coin, String contract, String txHash, int confirmations,
                           int requiredConfirmations, String signedTxHex, String expiresAt,
                           Long nonce, String actualFee, String actualFeeFiat, String createdAt,
                           String updatedAt, String error) {
        this(uuid, status, network, chainFamily, fromAddress, toAddress, type, value, coin,
                contract, txHash, confirmations, requiredConfirmations, signedTxHex, expiresAt,
                nonce, actualFee, actualFeeFiat, createdAt, updatedAt, error, null);
    }

    /** The 0.8.0 constructor; {@code confirmations} and {@code requiredConfirmations} are {@code 0}. */
    public TransactionInfo(String uuid, String status, Chain network, String chainFamily,
                           String fromAddress, String toAddress, String type, String value,
                           String coin, String contract, String txHash, String signedTxHex,
                           String expiresAt, Long nonce, String actualFee, String actualFeeFiat,
                           String createdAt, String updatedAt, String error) {
        this(uuid, status, network, chainFamily, fromAddress, toAddress, type, value, coin,
                contract, txHash, 0, 0, signedTxHex, expiresAt, nonce, actualFee, actualFeeFiat,
                createdAt, updatedAt, error, null);
    }

    public boolean isTerminal() {
        return TxStatus.TERMINAL.contains(status);
    }

    public boolean succeeded() {
        return TxStatus.CONFIRMED.equals(status);
    }
}
