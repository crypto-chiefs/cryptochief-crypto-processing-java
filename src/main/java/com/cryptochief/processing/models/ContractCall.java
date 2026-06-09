package com.cryptochief.processing.models;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ContractCall(
        @JsonProperty("to") String to,
        @JsonProperty("value") String value,
        @JsonProperty("data") String data,
        @JsonProperty("accounts") List<SolanaAccount> accounts,
        @JsonProperty("bounce") Boolean bounce
) {}
