package com.cryptochief.processing.models;

import com.cryptochief.processing.Chain;
import com.fasterxml.jackson.annotation.JsonProperty;

public record TransactionStatusRequest(
        @JsonProperty("chain") Chain chain,
        @JsonProperty("hash") String hash
) {}
