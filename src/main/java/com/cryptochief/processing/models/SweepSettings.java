package com.cryptochief.processing.models;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Three layers, on purpose.
 *
 * <p>{@code effective} is what will actually happen, {@code override} is what this wallet
 * decides for itself (null if it decides nothing), and {@code projectDefault} is what it
 * falls back to. Only the three together answer "is this value mine or inherited" - the
 * difference between changing it here and changing it on the project. Inheritance is per
 * field: a wallet can override the mode and keep inheriting the fee mode.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record SweepSettings(
        @JsonProperty("wallet_address") String walletAddress,
        @JsonProperty("network_code") String networkCode,
        @JsonProperty("effective") SweepPolicy effective,
        @JsonProperty("override") SweepOverride override,
        @JsonProperty("project_default") SweepPolicy projectDefault
) {}
