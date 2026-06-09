package com.cryptochief.processing.webhook;

import com.cryptochief.processing.Chain;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record PayInWebhookEvent(
        @JsonProperty("event") String event,
        @JsonProperty("uuid") String uuid,
        @JsonProperty("order_id") String orderId,
        @JsonProperty("user_id") String userId,
        @JsonProperty("status") String status,
        @JsonProperty("prev_status") String prevStatus,
        @JsonProperty("mode") String mode,
        @JsonProperty("amount_crypto") String amountCrypto,
        @JsonProperty("amount_fiat") String amountFiat,
        @JsonProperty("fact_amount_crypto") String factAmountCrypto,
        @JsonProperty("fact_amount_fiat") String factAmountFiat,
        @JsonProperty("currency") String currency,
        @JsonProperty("payment_coin") String paymentCoin,
        @JsonProperty("payment_network") Chain paymentNetwork,
        @JsonProperty("to_address") String toAddress,
        @JsonProperty("txid") String txid
) {}
