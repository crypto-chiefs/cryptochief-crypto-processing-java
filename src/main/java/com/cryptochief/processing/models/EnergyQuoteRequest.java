package com.cryptochief.processing.models;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * A price request for renting TRON energy for {@code receiveAddress} - the sender of the
 * transfer the energy is rented for, i.e. the address it will be delegated to. Both optional
 * fields default on the server side when omitted.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record EnergyQuoteRequest(
        @JsonProperty("receive_address") String receiveAddress,
        @JsonProperty("energy") Long energy,
        @JsonProperty("duration_sec") Long durationSec
) {
    /** A quote for the server's default energy amount and duration. */
    public static EnergyQuoteRequest of(String receiveAddress) {
        return new EnergyQuoteRequest(receiveAddress, null, null);
    }

    /** A quote for {@code energy} units held for {@code durationSec} seconds. */
    public static EnergyQuoteRequest of(String receiveAddress, long energy, long durationSec) {
        return new EnergyQuoteRequest(receiveAddress, energy, durationSec);
    }
}
