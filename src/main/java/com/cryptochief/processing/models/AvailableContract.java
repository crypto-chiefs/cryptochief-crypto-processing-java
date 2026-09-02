package com.cryptochief.processing.models;

import com.cryptochief.processing.Chain;
import com.cryptochief.processing.ChainFamily;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * One asset: a coin on a network.
 *
 * <p>The same shape on both catalogues - the project's own
 * ({@code /v1/blockchain/contracts/available}) and the platform-wide one
 * ({@code /v1/blockchain/contracts/list}).
 *
 * <p>{@code contract} is an empty string for a native coin, not null - there is no contract
 * to name. {@code isTest} marks an asset that lives on a test network, which is the only
 * thing that distinguishes a worthless payment from a real one when the platform picks the
 * asset for you.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record AvailableContract(
        @JsonProperty("network") Chain network,
        @JsonProperty("coin") String coin,
        @JsonProperty("contract") String contract,
        @JsonProperty("chain_family") ChainFamily chainFamily,
        @JsonProperty("type") String type,
        @JsonProperty("is_test") boolean isTest,
        @JsonProperty("decimals") int decimals
) {}
