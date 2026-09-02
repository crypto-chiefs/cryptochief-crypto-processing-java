package com.cryptochief.processing.models;

import com.cryptochief.processing.Chain;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * One chain the platform's blockchain scanner is connected to.
 *
 * <p>Infrastructure-level information: which chains the platform can read blocks from right
 * now. It is not the project's asset catalogue - for what the project can actually be paid
 * in, use {@code contractsAvailable()}.
 *
 * <p>{@code name} is the chain key. {@code type} is the protocol family the scanner reads it
 * with, spelled the scanner's way ({@code evm}, {@code tron}, {@code solana}, ...), which is
 * not the upper-case {@link com.cryptochief.processing.ChainFamily} the rest of the API
 * uses - hence a plain string rather than that type.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record SupportedBlockchain(
        @JsonProperty("name") Chain name,
        @JsonProperty("type") String type
) {}
