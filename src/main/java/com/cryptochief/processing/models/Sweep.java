package com.cryptochief.processing.models;

import com.cryptochief.processing.Chain;
import com.cryptochief.processing.ChainFamily;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
/**
 * One transit &rarr; master movement.
 *
 * <p>A sweep is broadcast first and confirmed after: {@link SweepStatus#BROADCASTED} means
 * the transaction is out and not yet confirmed, {@link SweepStatus#COMPLETED} means the
 * chain confirmed it, with {@code sweepConfirmations} and {@code completedAt} filled in.
 * The platform used to report {@code completed} at broadcast, so a sweep could read as
 * settled while its transaction was still unconfirmed or had been dropped.
 *
 * <p>{@code gasFeeHuman}, {@code gasFeeFiat}, {@code serviceFeeFiat} and {@code updatedAt}
 * are never populated - they were guesses at a shape the API does not send. The fees it
 * does send are the {@code totalFeeUsd} / gas-pump / {@code real*} fields.
 */
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
) {}
