package com.cryptochief.processing.models;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * A resolved set of sweep rules.
 *
 * <p>{@code thresholdAmountUsd} is meaningful only when the mode is
 * {@link SweepPolicyMode#THRESHOLD}. {@code source} names the layer the mode came from -
 * {@code wallet_network}, {@code wallet}, {@code project} or {@code default} - and is
 * present on the effective policy, where the question arises.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record SweepPolicy(
        @JsonProperty("type_work") String typeWork,
        @JsonProperty("threshold_amount_usd") String thresholdAmountUsd,
        @JsonProperty("fee_mode") String feeMode,
        @JsonProperty("source") String source
) {}
