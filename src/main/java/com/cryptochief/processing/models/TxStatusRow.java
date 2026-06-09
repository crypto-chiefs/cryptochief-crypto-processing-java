package com.cryptochief.processing.models;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record TxStatusRow(
        @JsonProperty("confirmations") int confirmations,
        @JsonProperty("fee") String fee,
        @JsonProperty("human_fee") String humanFee,
        @JsonProperty("block_number") Long blockNumber,
        @JsonProperty("status") String status
) {}
