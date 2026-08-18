package com.cryptochief.processing.models;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/** Pending topup invoice: pay via {@code paymentLink}; {@code expiredAt} is unix seconds, may be null. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record CreditsTopup(
        @JsonProperty("invoice_id") long invoiceId,
        @JsonProperty("payment_link") String paymentLink,
        @JsonProperty("amount") String amount,
        @JsonProperty("currency") String currency,
        @JsonProperty("status") String status,
        @JsonProperty("order_uuid") String orderUuid,
        @JsonProperty("expired_at") Long expiredAt
) {}
