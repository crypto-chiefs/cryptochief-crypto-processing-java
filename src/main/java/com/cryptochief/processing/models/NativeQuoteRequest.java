package com.cryptochief.processing.models;

import com.cryptochief.processing.Chain;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * A price request for buying the native coin of {@code network} (TRX, ETH, BNB, SOL, TON, ...)
 * delivered to {@code receiveAddress} - any address, the merchant pays the transfer. {@code amount}
 * is in human units as a decimal string ({@code "0.05"}), the form the quote and the order echo back.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record NativeQuoteRequest(
        @JsonProperty("network") Chain network,
        @JsonProperty("receive_address") String receiveAddress,
        @JsonProperty("amount") String amount
) {
    /** A quote for {@code amount} of the native coin of {@code network}. */
    public static NativeQuoteRequest of(Chain network, String receiveAddress, String amount) {
        return new NativeQuoteRequest(network, receiveAddress, amount);
    }
}
