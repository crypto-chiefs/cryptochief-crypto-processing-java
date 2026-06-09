package com.cryptochief.processing.models;

import com.cryptochief.processing.Chain;
import com.fasterxml.jackson.annotation.JsonProperty;

public record ForceSweepRequest(
        @JsonProperty("address") String address,
        @JsonProperty("network_code") Chain networkCode
) {}
