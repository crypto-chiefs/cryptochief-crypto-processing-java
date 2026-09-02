package com.cryptochief.processing.models;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * The body of {@code /v1/wallets/history} - the pay-ins that used one deposit address.
 *
 * <p>{@code address} is required and matched case-insensitively, so either spelling of an
 * EVM address works. An address that is not the project's yields an empty page rather than
 * an error, so an empty result says nothing about whether the address exists.
 *
 * <p>{@code dateFrom} / {@code dateTo} filter on creation date and are written
 * {@code YYYY-MM-DDTHH:MM:SS+HH:MM}. {@code page} defaults to 1 and {@code pageSize} to 20,
 * with 100 the maximum the platform accepts.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record WalletHistoryQuery(
        @JsonProperty("address") String address,
        @JsonProperty("date_from") String dateFrom,
        @JsonProperty("date_to") String dateTo,
        @JsonProperty("page") Integer page,
        @JsonProperty("page_size") Integer pageSize
) {

    /** Every pay-in that used this address, first page, platform defaults. */
    public static WalletHistoryQuery forAddress(String address) {
        return new WalletHistoryQuery(address, null, null, null, null);
    }
}
