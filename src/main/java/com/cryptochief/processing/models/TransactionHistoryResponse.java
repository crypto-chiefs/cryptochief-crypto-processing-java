package com.cryptochief.processing.models;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record TransactionHistoryResponse(
        @JsonProperty("items") List<TransactionInfo> items,
        @JsonProperty("meta") HistoryMeta meta
) {}
