package com.cryptochief.processing.models;

import com.cryptochief.processing.Chain;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record StaticDepositHistoryQuery(
        @JsonProperty("address") String address,
        @JsonProperty("status") String status,
        @JsonProperty("coin") String coin,
        @JsonProperty("network") Chain network,
        @JsonProperty("date_from") String dateFrom,
        @JsonProperty("date_to") String dateTo,
        @JsonProperty("page") Integer page,
        @JsonProperty("page_size") Integer pageSize
) {
    public static StaticDepositHistoryQuery empty() {
        return new StaticDepositHistoryQuery(null, null, null, null, null, null, null, null);
    }
}
