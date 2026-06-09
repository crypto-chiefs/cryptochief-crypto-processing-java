package com.cryptochief.processing.models;

import com.fasterxml.jackson.annotation.JsonProperty;

public record PayoutFeeInfo(
        @JsonProperty("fee_mode") String feeMode,
        @JsonProperty("estimated_fiat") String estimatedFiat,
        @JsonProperty("estimated_coin") String estimatedCoin,
        @JsonProperty("estimated_asset") String estimatedAsset
) {}
