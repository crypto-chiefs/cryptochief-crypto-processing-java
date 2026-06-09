package com.cryptochief.processing.models;

import com.cryptochief.processing.Chain;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record WalletCoinBalance(
        @JsonProperty("address") String address,
        @JsonProperty("chain") Chain chain,
        @JsonProperty("coin") String coin,
        @JsonProperty("contract") String contract,
        @JsonProperty("decimals") int decimals,
        @JsonProperty("value") String value,
        @JsonProperty("human_value") String humanValue,
        @JsonProperty("amount_usd") String amountUsd,
        @JsonProperty("timestamp") Long timestamp
) {}
