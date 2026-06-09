package com.cryptochief.processing.models;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record WalletBalanceRow(
        @JsonProperty("contract") String contract,
        @JsonProperty("address") String address,
        @JsonProperty("value") String value,
        @JsonProperty("human_value") String humanValue,
        @JsonProperty("decimals") int decimals
) {}
