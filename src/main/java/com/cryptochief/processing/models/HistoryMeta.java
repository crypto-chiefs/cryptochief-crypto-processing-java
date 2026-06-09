package com.cryptochief.processing.models;

import com.fasterxml.jackson.annotation.JsonProperty;

public record HistoryMeta(
        @JsonProperty("page") int page,
        @JsonProperty("page_size") int pageSize,
        @JsonProperty("total") int total,
        @JsonProperty("total_pages") Integer totalPages
) {}
