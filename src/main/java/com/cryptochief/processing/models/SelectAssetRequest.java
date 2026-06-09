package com.cryptochief.processing.models;

import com.cryptochief.processing.Chain;
import com.fasterxml.jackson.annotation.JsonProperty;

public record SelectAssetRequest(
        @JsonProperty("uuid") String uuid,
        @JsonProperty("coin") String coin,
        @JsonProperty("network") Chain network
) {}
