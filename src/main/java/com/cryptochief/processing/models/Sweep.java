package com.cryptochief.processing.models;

import com.cryptochief.processing.Chain;
import com.cryptochief.processing.ChainFamily;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * One transit &rarr; master movement.
 *
 * <p>While a sweep is {@link SweepStatus#BROADCASTED}, {@code sweepConfirmations} grows. At
 * {@code requiredConfirmations}, the finality depth of its network, the sweep becomes
 * {@link SweepStatus#COMPLETED}. Settled: status {@code completed} and {@code sweepConfirmations}
 * above zero, or the {@code sweep.confirmed} webhook. On older records {@code completed} can
 * have {@code 0} and is then not settled. A count above zero alone is not enough: a
 * {@code broadcasted} sweep has one too.
 *
 * <p>{@code completedAt} is when the sweep transaction was sent (for
 * {@link SweepStatus#WAITING_GAS}, {@link SweepStatus#FAILED} and {@link SweepStatus#SKIPPED},
 * when that status was recorded). It is not a settlement signal.
 *
 * <p>{@code gasFeeHuman}, {@code gasFeeFiat}, {@code serviceFeeFiat} and {@code updatedAt}
 * are never populated - they were guesses at a shape the API does not send. The fees it
 * does send are the {@code totalFeeUsd} / gas-pump / {@code real*} fields.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record Sweep(
        @JsonProperty("task_id") String taskId,
        @JsonProperty("sweep_tx_hash") String sweepTxHash,
        @JsonProperty("gas_pump_tx_hash") String gasPumpTxHash,
        @JsonProperty("status") String status,
        @JsonProperty("wallet_address") String walletAddress,
        @JsonProperty("chain") Chain chain,
        @JsonProperty("chain_family") ChainFamily chainFamily,
        @JsonProperty("asset_symbol") String assetSymbol,
        @JsonProperty("asset_type") String assetType,
        @JsonProperty("amount_human") String amountHuman,
        @JsonProperty("type_work") String typeWork,
        @JsonProperty("sweep_confirmations") Integer sweepConfirmations,
        @JsonProperty("required_confirmations") Integer requiredConfirmations,
        @JsonProperty("completed_at") String completedAt,
        @JsonProperty("total_fee_usd") String totalFeeUsd,
        @JsonProperty("gas_pump_source") String gasPumpSource,
        @JsonProperty("gas_pump_fee_human") String gasPumpFeeHuman,
        @JsonProperty("gas_pump_fee_usd") String gasPumpFeeUsd,
        @JsonProperty("sweep_fee_human") String sweepFeeHuman,
        @JsonProperty("sweep_fee_usd") String sweepFeeUsd,
        @JsonProperty("real_gas_pump_fee_human") String realGasPumpFeeHuman,
        @JsonProperty("real_gas_pump_fee_usd") String realGasPumpFeeUsd,
        @JsonProperty("real_sweep_fee_human") String realSweepFeeHuman,
        @JsonProperty("real_sweep_fee_usd") String realSweepFeeUsd,
        @JsonProperty("created_at") String createdAt,
        // Never populated - see the note on this record. No @Deprecated: on a record
        // component the annotation lands on the field, where javac says it has no
        // effect, and this build treats warnings as errors.
        @JsonProperty("gas_fee_human") String gasFeeHuman,
        @JsonProperty("gas_fee_fiat") String gasFeeFiat,
        @JsonProperty("service_fee_fiat") String serviceFeeFiat,
        @JsonProperty("updated_at") String updatedAt
) {
    /** The 0.8.0 constructor; {@code requiredConfirmations} is {@code null}. */
    public Sweep(String taskId, String sweepTxHash, String gasPumpTxHash, String status,
                 String walletAddress, Chain chain, ChainFamily chainFamily, String assetSymbol,
                 String assetType, String amountHuman, String typeWork, Integer sweepConfirmations,
                 String completedAt, String totalFeeUsd, String gasPumpSource,
                 String gasPumpFeeHuman, String gasPumpFeeUsd, String sweepFeeHuman,
                 String sweepFeeUsd, String realGasPumpFeeHuman, String realGasPumpFeeUsd,
                 String realSweepFeeHuman, String realSweepFeeUsd, String createdAt,
                 String gasFeeHuman, String gasFeeFiat, String serviceFeeFiat, String updatedAt) {
        this(taskId, sweepTxHash, gasPumpTxHash, status, walletAddress, chain, chainFamily,
                assetSymbol, assetType, amountHuman, typeWork, sweepConfirmations, null,
                completedAt, totalFeeUsd, gasPumpSource, gasPumpFeeHuman, gasPumpFeeUsd,
                sweepFeeHuman, sweepFeeUsd, realGasPumpFeeHuman, realGasPumpFeeUsd,
                realSweepFeeHuman, realSweepFeeUsd, createdAt, gasFeeHuman, gasFeeFiat,
                serviceFeeFiat, updatedAt);
    }
}
