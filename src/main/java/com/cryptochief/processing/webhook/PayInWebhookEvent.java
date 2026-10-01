package com.cryptochief.processing.webhook;

import com.cryptochief.processing.Chain;
import com.cryptochief.processing.models.PayInPayment;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * A pay-in (invoice) webhook: {@code invoice.paid} and the other {@code invoice.*} events.
 *
 * <p>On an order created with {@code is_payment_multiple} the payload additionally carries
 * {@code isPaymentMultiple}, {@code receivedAmountCrypto} (the running total),
 * {@code remainingAmountCrypto} and {@code payments} - one entry per receipt. They are absent
 * on single-payment orders.
 *
 * <p>{@link #EVENT_WRONG_AMOUNT_WAITING} fires on EVERY receipt while the invoiced amount is
 * not reached yet; {@link #EVENT_LATE_PAYMENT} fires when a payment arrives after the order
 * reached its final status, inside the observation window.
 */
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
        @JsonProperty("txid") String txid,
        @JsonProperty("is_payment_multiple") Boolean isPaymentMultiple,
        @JsonProperty("received_amount_crypto") String receivedAmountCrypto,
        @JsonProperty("remaining_amount_crypto") String remainingAmountCrypto,
        @JsonProperty("payments") List<PayInPayment> payments
) {
    /** A payment arrived but the invoiced amount is not reached yet; sent on every receipt. */
    public static final String EVENT_WRONG_AMOUNT_WAITING = "invoice.wrong_amount_waiting";

    /** A payment arrived after the order's final status, inside the observation window. */
    public static final String EVENT_LATE_PAYMENT = "invoice.late_payment";

    /** The 0.13.0 constructor; the multiple-payment fields are {@code null}. */
    public PayInWebhookEvent(String event, String uuid, String orderId, String userId,
                             String status, String prevStatus, String mode, String amountCrypto,
                             String amountFiat, String factAmountCrypto, String factAmountFiat,
                             String currency, String paymentCoin, Chain paymentNetwork,
                             String toAddress, String txid) {
        this(event, uuid, orderId, userId, status, prevStatus, mode, amountCrypto, amountFiat,
                factAmountCrypto, factAmountFiat, currency, paymentCoin, paymentNetwork,
                toAddress, txid, null, null, null, null);
    }
}
