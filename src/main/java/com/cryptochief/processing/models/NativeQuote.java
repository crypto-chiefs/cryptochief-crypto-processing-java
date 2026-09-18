package com.cryptochief.processing.models;

import com.cryptochief.processing.Chain;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * A price the native coin service stands behind until {@code expiresAt} (about 90 seconds, single
 * use); free to request.
 *
 * <p>The price builds up from the {@code amount} coins at the {@code coinUsd} rate plus the
 * {@code transferFee} of the platform's own outgoing transfer at the same rate: {@code coinPriceUsd}
 * is the coins, {@code transferFeeUsd} the transfer, {@code subtotalUsd} the two together and
 * {@code totalUsd} the full sale price. Dollar figures are decimal strings. {@code credits} is what
 * the buy will actually take from the credits
 * balance - compare it against {@code credits().balance().creditsBalance()} before ordering.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record NativeQuote(
        @JsonProperty("ref") String ref,
        @JsonProperty("network") Chain network,
        @JsonProperty("receive_address") String receiveAddress,
        @JsonProperty("amount") String amount,
        @JsonProperty("coin_price_usd") String coinPriceUsd,
        @JsonProperty("transfer_fee") String transferFee,
        @JsonProperty("transfer_fee_usd") String transferFeeUsd,
        @JsonProperty("subtotal_usd") String subtotalUsd,
        @JsonProperty("total_usd") String totalUsd,
        @JsonProperty("credits") Long credits,
        @JsonProperty("coin_usd") String coinUsd,
        @JsonProperty("expires_at") String expiresAt,
        @JsonProperty("expires_in_sec") long expiresInSec
) {}
