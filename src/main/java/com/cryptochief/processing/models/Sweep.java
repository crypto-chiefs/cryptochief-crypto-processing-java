package com.cryptochief.processing.models;

import com.cryptochief.processing.Chain;
import com.cryptochief.processing.ChainFamily;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record Sweep(
        @JsonProperty("task_id") String taskId,
        @JsonProperty("sweep_tx_hash") String sweepTxHash,
        @JsonProperty("status") String status,
        @JsonProperty("wallet_address") String walletAddress,
        @JsonProperty("chain") Chain chain,
        @JsonProperty("chain_family") ChainFamily chainFamily,
        @JsonProperty("asset_symbol") String assetSymbol,
        @JsonProperty("asset_type") String assetType,
        @JsonProperty("amount_human") String amountHuman,
        @JsonProperty("gas_fee_human") String gasFeeHuman,
        @JsonProperty("gas_fee_fiat") String gasFeeFiat,
        @JsonProperty("service_fee_fiat") String serviceFeeFiat,
        @JsonProperty("created_at") String createdAt,
        @JsonProperty("updated_at") String updatedAt
) {}
