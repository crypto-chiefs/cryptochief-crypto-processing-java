package com.cryptochief.processing.models;

import com.cryptochief.processing.Chain;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * A pay-in order, the answer of create, info and history.
 *
 * <p>On an order created with {@code is_payment_multiple} the response additionally carries
 * {@code isPaymentMultiple}, {@code receivedAmountCrypto} (the running total),
 * {@code remainingAmountCrypto} and {@code payments} - one entry per receipt, the same shape
 * the pay-in webhooks report. They are absent ({@code null}) on single-payment orders.
 */
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
        @JsonProperty("updated_at") String updatedAt,
        @JsonProperty("is_payment_multiple") Boolean isPaymentMultiple,
        @JsonProperty("received_amount_crypto") String receivedAmountCrypto,
        @JsonProperty("remaining_amount_crypto") String remainingAmountCrypto,
        @JsonProperty("payments") List<PayInPayment> payments
) {
    /** The 0.13.0 constructor; the multiple-payment fields are {@code null}. */
    public PayIn(String type, String uuid, String orderId, String userId, String status,
                 String mode, String amountCrypto, String amountFiat, String currency,
                 String paymentCoin, Chain paymentNetwork, String toAddress,
                 List<CoinOption> coins, String paymentLink, String urlCallback,
                 String urlSuccess, String urlError, String additionalData, Boolean canCancel,
                 String expiredAt, String createdAt, String updatedAt) {
        this(type, uuid, orderId, userId, status, mode, amountCrypto, amountFiat, currency,
                paymentCoin, paymentNetwork, toAddress, coins, paymentLink, urlCallback,
                urlSuccess, urlError, additionalData, canCancel, expiredAt, createdAt,
                updatedAt, null, null, null, null);
    }

    public boolean isTerminal() {
        return PayInStatus.TERMINAL.contains(status);
    }

    public boolean succeeded() {
        return PayInStatus.PAID.equals(status);
    }
}
