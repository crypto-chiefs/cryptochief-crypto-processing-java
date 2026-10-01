package com.cryptochief.processing.models;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * One receipt toward a multiple-payment pay-in, an item of {@link PayIn#payments()} on the
 * order responses and of the pay-in webhook's {@code payments}.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record PayInPayment(
        @JsonProperty("txid") String txid,
        @JsonProperty("amount_crypto") String amountCrypto,
        @JsonProperty("confirmations") Integer confirmations,
        @JsonProperty("status") String status,
        @JsonProperty("seen_at") String seenAt
) {}
