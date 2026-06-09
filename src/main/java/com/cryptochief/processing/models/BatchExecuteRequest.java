package com.cryptochief.processing.models;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record BatchExecuteRequest(
        @JsonProperty("url_callback") String urlCallback,
        @JsonProperty("items") List<ExecutePayoutRequest> items
) {}
