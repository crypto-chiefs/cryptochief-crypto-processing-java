package com.cryptochief.processing.models;

import com.fasterxml.jackson.annotation.JsonProperty;

public record PayoutSource(
        @JsonProperty("address") String address,
        @JsonProperty("amount") String amount,
        @JsonProperty("coin") String coin
) {}
