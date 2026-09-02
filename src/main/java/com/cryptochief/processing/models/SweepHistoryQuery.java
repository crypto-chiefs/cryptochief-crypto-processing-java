package com.cryptochief.processing.models;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * The body of {@code /v1/sweeps/history}.
 *
 * <p>Every filter is optional and every one left null widens the answer rather than
 * narrowing it: no {@code mode} includes both {@code auto} and {@code force}, and no
 * {@code status} includes every status - {@link SweepStatus#SKIPPED} among them, which is a
 * normal outcome rather than a failure and is easy to be surprised by in a total.
 *
 * <p>{@code search} is a substring match on the wallet address, the sweep or gas-pump
 * transaction hash, and the {@code task_id}.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record SweepHistoryQuery(
        @JsonProperty("mode") String mode,
        @JsonProperty("page") Integer page,
        @JsonProperty("page_size") Integer pageSize,
        @JsonProperty("status") String status,
        @JsonProperty("search") String search
) {

    /**
     * The query without the two filters, which then match everything. Kept so code written
     * before they existed still compiles.
     */
    public SweepHistoryQuery(String mode, Integer page, Integer pageSize) {
        this(mode, page, pageSize, null, null);
    }

    /** Every sweep the project has made, unfiltered. */
    public static SweepHistoryQuery empty() {
        return new SweepHistoryQuery(null, null, null, null, null);
    }

    /** The same query, narrowed to one {@link SweepStatus}. */
    public SweepHistoryQuery withStatus(String status) {
        return new SweepHistoryQuery(mode, page, pageSize, status, search);
    }

    /** The same query, narrowed to sweeps matching a wallet address, hash or task id. */
    public SweepHistoryQuery withSearch(String search) {
        return new SweepHistoryQuery(mode, page, pageSize, status, search);
    }
}
