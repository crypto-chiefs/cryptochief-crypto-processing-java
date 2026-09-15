package com.cryptochief.processing.models;

import com.cryptochief.processing.Chain;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * One manual withdrawal, from {@code /v1/withdrawal/info} or {@code /v1/withdrawal/history}.
 * Status values are in {@link WithdrawalStatus}.
 *
 * <p>{@code confirmations} is optional, absent until the transaction is in a block.
 * {@code requiredConfirmations} is always present. The withdrawal stays
 * {@link WithdrawalStatus#CONFIRM_CHECK} until {@code confirmations} reaches
 * {@code requiredConfirmations}, then becomes {@link WithdrawalStatus#COMPLETED}.
 *
 * <p>{@code errorReason} is the machine code of a {@code failed} withdrawal.
 *
 * <p>{@code contract}, {@code amountFiat}, {@code updatedAt}, {@code confirmedAt} and
 * {@code error} are never populated; read {@code completedAt} and {@code errorReason} instead.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record Withdrawal(
        @JsonProperty("uuid") String uuid,
        @JsonProperty("status") String status,
        @JsonProperty("network") Chain network,
        @JsonProperty("coin") String coin,
        @JsonProperty("contract") String contract,
        @JsonProperty("amount") String amount,
        @JsonProperty("amount_fiat") String amountFiat,
        @JsonProperty("from_address") String fromAddress,
        @JsonProperty("to_address") String toAddress,
        @JsonProperty("need_refuel") boolean needRefuel,
        @JsonProperty("refuel_tx_hash") String refuelTxHash,
        @JsonProperty("refuel_status") String refuelStatus,
        @JsonProperty("tx_hash") String txHash,
        @JsonProperty("confirmations") Integer confirmations,
        @JsonProperty("required_confirmations") Integer requiredConfirmations,
        @JsonProperty("error_reason") String errorReason,
        @JsonProperty("estimated_fee_fiat") String estimatedFeeFiat,
        @JsonProperty("actual_fee_fiat") String actualFeeFiat,
        @JsonProperty("fee_mode") String feeMode,
        @JsonProperty("created_at") String createdAt,
        @JsonProperty("completed_at") String completedAt,
        @JsonProperty("updated_at") String updatedAt,
        @JsonProperty("confirmed_at") String confirmedAt,
        @JsonProperty("error") String error
) {
    /** The 0.8.0 constructor; {@code needRefuel} is {@code false}, the other new components {@code null}. */
    public Withdrawal(String uuid, String status, Chain network, String coin, String contract,
                      String amount, String amountFiat, String fromAddress, String toAddress,
                      String txHash, String createdAt, String updatedAt, String confirmedAt,
                      String error) {
        this(uuid, status, network, coin, contract, amount, amountFiat, fromAddress, toAddress,
                false, null, null, txHash, null, null, null, null, null, null,
                createdAt, null, updatedAt, confirmedAt, error);
    }

    /** Whether the withdrawal is {@code completed} or {@code failed}. */
    public boolean isTerminal() {
        return WithdrawalStatus.TERMINAL.contains(status);
    }

    /** Whether the withdrawal is {@code completed}. */
    public boolean succeeded() {
        return WithdrawalStatus.COMPLETED.equals(status);
    }
}
