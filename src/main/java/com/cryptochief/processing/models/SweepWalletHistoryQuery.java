package com.cryptochief.processing.models;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * The body of {@code /v1/sweeps/wallet/history}. {@code address} is required; the rest
 * narrow the answer and, left null, widen it - no {@code status} includes every status,
 * {@link SweepStatus#SKIPPED} among them.
 *
 * <p>{@code search} is a substring match on the sweep or gas-pump transaction hash and the
 * {@code task_id}. The wallet address is not among them here: it is already the question.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record SweepWalletHistoryQuery(
        @JsonProperty("address") String address,
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
    public SweepWalletHistoryQuery(String address, String mode, Integer page, Integer pageSize) {
        this(address, mode, page, pageSize, null, null);
    }

    /** Every sweep of one wallet, unfiltered. */
    public static SweepWalletHistoryQuery forAddress(String address) {
        return new SweepWalletHistoryQuery(address, null, null, null, null, null);
    }

    /** The same query, narrowed to one {@link SweepStatus}. */
    public SweepWalletHistoryQuery withStatus(String status) {
        return new SweepWalletHistoryQuery(address, mode, page, pageSize, status, search);
    }

    /** The same query, narrowed to sweeps matching a transaction hash or task id. */
    public SweepWalletHistoryQuery withSearch(String search) {
        return new SweepWalletHistoryQuery(address, mode, page, pageSize, status, search);
    }
}
