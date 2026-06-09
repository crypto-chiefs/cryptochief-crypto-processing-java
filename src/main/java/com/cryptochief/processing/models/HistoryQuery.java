package com.cryptochief.processing.models;

import com.cryptochief.processing.Chain;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record HistoryQuery(
        @JsonProperty("page") Integer page,
        @JsonProperty("page_size") Integer pageSize,
        @JsonProperty("status") String status,
        @JsonProperty("coin") String coin,
        @JsonProperty("network") Chain network,
        @JsonProperty("date_from") String dateFrom,
        @JsonProperty("date_to") String dateTo
) {
    public static HistoryQuery empty() {
        return new HistoryQuery(null, null, null, null, null, null, null);
    }
}
