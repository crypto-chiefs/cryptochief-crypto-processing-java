package com.cryptochief.processing.models;

import com.cryptochief.processing.Chain;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * A native coin purchase order. {@code receiveAddress} is where the coins are sent - any address,
 * the merchant pays the transfer. {@code amount} is in human units as a decimal string
 * ({@code "0.05"}). {@code quoteRef} holds the price of a {@link NativeQuote} that has not expired
 * yet; with one, the network, address and amount come from the quote and may be omitted here.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record NativeBuyRequest(
        @JsonProperty("network") Chain network,
        @JsonProperty("receive_address") String receiveAddress,
        @JsonProperty("amount") String amount,
        @JsonProperty("quote_ref") String quoteRef
) {
    /** Buy {@code amount} of the native coin of {@code network} at the current price. */
    public static NativeBuyRequest of(Chain network, String receiveAddress, String amount) {
        return new NativeBuyRequest(network, receiveAddress, amount, null);
    }

    /** Buy at the price and parameters of the quote {@code ref} names. */
    public static NativeBuyRequest ofQuote(String quoteRef) {
        return new NativeBuyRequest(null, null, null, quoteRef);
    }
}
