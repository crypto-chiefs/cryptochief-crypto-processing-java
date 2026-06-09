package com.cryptochief.processing.models;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record WithdrawalHistoryResponse(
        @JsonProperty("items") List<Withdrawal> items,
        @JsonProperty("meta") HistoryMeta meta
) {}
