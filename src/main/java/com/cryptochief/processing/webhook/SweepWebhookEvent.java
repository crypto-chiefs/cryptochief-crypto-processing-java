package com.cryptochief.processing.webhook;

import com.cryptochief.processing.Chain;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Funds swept off a deposit wallet, confirmed on chain. Event name
 * {@code sweep.confirmed} - the only sweep event the platform emits.
 *
 * <p>There is deliberately no {@code sweep.broadcasted}: "we sent it" is not
 * something you can act on, and an event that means "maybe" is one more thing to
 * reconcile.
 *
 * <p>A {@code static_deposit.paid} tells you a customer paid you. This tells you
 * the money has finished moving into your own custody - until it fires, the
 * balance still sits on the deposit address. Reconciliation, treasury reporting
 * and "funds available to pay out" all key off this event, not off the deposit.
 *
 * <p>Sweeps run on static deposit wallets AND on the transit wallets issued per
 * pay-in order; both deliver here, to the callback URL configured for the wallet
 * the funds left.
 *
 * @param taskId             the sweeper task; one sweep settles once, so use it
 *                           as your idempotency key
 * @param status             always {@code completed} - a sweep reaches you in no
 *                           other state
 * @param walletAddress      the wallet the funds left, i.e. the address your
 *                           customer paid into
 * @param toAddress          the master wallet they landed on
 * @param assetType          {@code native} or {@code token}
 * @param gasPumpTxHash      set when the platform had to fund gas on the wallet
 *                           before it could sweep
 * @param sweepConfirmations what makes this event true rather than hopeful, and
 *                           never zero; it travels with the event rather than
 *                           being implied by it, because "confirmed" is not the
 *                           same number on every chain and your own finality
 *                           policy needs the count to apply it
 * @param confirmedAt        when the chain was observed to hold the sweep; NOT
 *                           the task's completion timestamp, which is stamped on
 *                           every terminal outcome including failures and so
 *                           says nothing about settlement
 * @param typeWork           what triggered it: {@code momentum}, {@code
 *                           threshold} or {@code force}
 * @param totalFeeUsd        what the sweep cost: network fee plus any gas or
 *                           energy the platform fronted to make it possible
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record SweepWebhookEvent(
        @JsonProperty("event") String event,
        @JsonProperty("task_id") String taskId,
        @JsonProperty("status") String status,
        @JsonProperty("wallet_address") String walletAddress,
        @JsonProperty("to_address") String toAddress,
        @JsonProperty("network") Chain network,
        @JsonProperty("chain_family") String chainFamily,
        @JsonProperty("asset_symbol") String assetSymbol,
        @JsonProperty("asset_contract") String assetContract,
        @JsonProperty("asset_type") String assetType,
        @JsonProperty("amount_raw") String amountRaw,
        @JsonProperty("amount_human") String amountHuman,
        @JsonProperty("sweep_tx_hash") String sweepTxHash,
        @JsonProperty("gas_pump_tx_hash") String gasPumpTxHash,
        @JsonProperty("sweep_confirmations") int sweepConfirmations,
        @JsonProperty("confirmed_at") String confirmedAt,
        @JsonProperty("type_work") String typeWork,
        @JsonProperty("total_fee_usd") String totalFeeUsd
) {
    /** The only sweep event the platform emits. */
    public static final String EVENT_CONFIRMED = "sweep.confirmed";
}
