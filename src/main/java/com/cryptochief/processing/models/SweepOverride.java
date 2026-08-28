package com.cryptochief.processing.models;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * What one wallet decides for itself.
 *
 * <p>A {@code null} field is not overridden - it is inherited, which no ordinary value can
 * express. An empty {@code networkCode} covers the address on every network it exists on;
 * set, it covers that one network and takes precedence over the address-wide override.
 *
 * <p>{@code locked} means an operator pinned this policy: while it is set, a merchant
 * write answers {@code SWEEP_SETTINGS_LOCKED} and changes nothing.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record SweepOverride(
        @JsonProperty("network_code") String networkCode,
        @JsonProperty("type_work") String typeWork,
        @JsonProperty("threshold_amount_usd") String thresholdAmountUsd,
        @JsonProperty("fee_mode") String feeMode,
        @JsonProperty("source") String source,
        @JsonProperty("locked") boolean locked
) {}
