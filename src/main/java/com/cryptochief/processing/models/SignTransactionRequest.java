package com.cryptochief.processing.models;

import com.cryptochief.processing.Chain;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record SignTransactionRequest(
        @JsonProperty("network") Chain network,
        @JsonProperty("from_address") String fromAddress,
        @JsonProperty("type") String type,
        @JsonProperty("to_address") String toAddress,
        @JsonProperty("value") String value,
        @JsonProperty("contract") String contract,
        @JsonProperty("calls") List<ContractCall> calls,
        @JsonProperty("url_callback") String urlCallback
) {}
