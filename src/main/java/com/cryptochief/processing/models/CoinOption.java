package com.cryptochief.processing.models;

import com.cryptochief.processing.Chain;
import com.cryptochief.processing.ChainFamily;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record CoinOption(
        @JsonProperty("chain_family") ChainFamily chainFamily,
        @JsonProperty("coin") String coin,
        @JsonProperty("network") Chain network,
        @JsonProperty("contract") String contract
) {}
