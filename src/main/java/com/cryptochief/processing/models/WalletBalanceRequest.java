package com.cryptochief.processing.models;

import com.cryptochief.processing.Chain;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record WalletBalanceRequest(
        @JsonProperty("chain") Chain chain,
        @JsonProperty("addresses") List<String> addresses,
        @JsonProperty("contracts") List<String> contracts
) {}
