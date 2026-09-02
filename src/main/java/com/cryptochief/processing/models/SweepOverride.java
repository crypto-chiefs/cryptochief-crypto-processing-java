package com.cryptochief.processing.models;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * What one wallet decides for itself.
 *
 * <p>A {@code null} field is not overridden - it is inherited, which no ordinary value can
 * express. That applies to {@code gasSource} as much as the rest: null here means this layer
 * does not decide, <strong>not</strong> that rented energy is switched off. An empty
 * {@code networkCode} covers the address on every network it exists on; set, it covers that
 * one network and takes precedence over the address-wide override.
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
        @JsonProperty("gas_source") String gasSource,
        @JsonProperty("source") String source,
        @JsonProperty("locked") boolean locked
) {}
