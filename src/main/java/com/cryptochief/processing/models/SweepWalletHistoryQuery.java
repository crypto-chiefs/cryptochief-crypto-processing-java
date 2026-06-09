package com.cryptochief.processing.models;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record SweepWalletHistoryQuery(
        @JsonProperty("address") String address,
        @JsonProperty("mode") String mode,
        @JsonProperty("page") Integer page,
        @JsonProperty("page_size") Integer pageSize
) {}
