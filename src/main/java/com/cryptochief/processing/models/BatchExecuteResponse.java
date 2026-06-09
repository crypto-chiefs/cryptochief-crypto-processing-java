package com.cryptochief.processing.models;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record BatchExecuteResponse(
        @JsonProperty("batch_uuid") String batchUuid,
        @JsonProperty("total") int total,
        @JsonProperty("accepted") int accepted,
        @JsonProperty("rejected") int rejected,
        @JsonProperty("items") List<BatchItemResult> items
) {}
