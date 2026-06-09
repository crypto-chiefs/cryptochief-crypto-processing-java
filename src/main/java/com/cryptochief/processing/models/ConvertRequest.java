package com.cryptochief.processing.models;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ConvertRequest(
        @JsonProperty("provider") String provider,
        @JsonProperty("from") String from,
        @JsonProperty("to") String to,
        @JsonProperty("amount") String amount
) {}
