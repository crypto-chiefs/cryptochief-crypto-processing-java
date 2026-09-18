package com.cryptochief.processing.models;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * An energy rental order. {@code receiveAddress} is the sender of the transfer the energy is
 * rented for - the address it is delegated to. {@code quoteRef} holds the price of an
 * {@link EnergyQuote} that has not expired yet; with one, the amount and duration come from the
 * quote and may be omitted here.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record EnergyRentRequest(
        @JsonProperty("receive_address") String receiveAddress,
        @JsonProperty("energy") Long energy,
        @JsonProperty("duration_sec") Long durationSec,
        @JsonProperty("quote_ref") String quoteRef
) {
    /** Rent {@code energy} units for {@code durationSec} seconds at the current price. */
    public static EnergyRentRequest of(String receiveAddress, long energy, long durationSec) {
        return new EnergyRentRequest(receiveAddress, energy, durationSec, null);
    }

    /** Rent at the price and parameters of the quote {@code ref} names. */
    public static EnergyRentRequest ofQuote(String quoteRef) {
        return new EnergyRentRequest(null, null, null, quoteRef);
    }
}
