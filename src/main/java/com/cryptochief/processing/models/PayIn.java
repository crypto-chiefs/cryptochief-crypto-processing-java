package com.cryptochief.processing.models;

import com.cryptochief.processing.Chain;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record PayIn(
        @JsonProperty("type") String type,
        @JsonProperty("uuid") String uuid,
        @JsonProperty("order_id") String orderId,
        @JsonProperty("user_id") String userId,
        @JsonProperty("status") String status,
        @JsonProperty("mode") String mode,
        @JsonProperty("amount_crypto") String amountCrypto,
        @JsonProperty("amount_fiat") String amountFiat,
        @JsonProperty("currency") String currency,
        @JsonProperty("payment_coin") String paymentCoin,
        @JsonProperty("payment_network") Chain paymentNetwork,
        @JsonProperty("to_address") String toAddress,
        @JsonProperty("coins") List<CoinOption> coins,
        @JsonProperty("payment_link") String paymentLink,
        @JsonProperty("url_callback") String urlCallback,
        @JsonProperty("url_success") String urlSuccess,
        @JsonProperty("url_error") String urlError,
        @JsonProperty("additional_data") String additionalData,
        @JsonProperty("can_cancel") Boolean canCancel,
        @JsonProperty("expired_at") String expiredAt,
        @JsonProperty("created_at") String createdAt,
        @JsonProperty("updated_at") String updatedAt
) {
    public boolean isTerminal() {
        return PayInStatus.TERMINAL.contains(status);
    }

    public boolean succeeded() {
        return PayInStatus.PAID.equals(status);
    }
}
