package com.cryptochief.processing.models;

import com.cryptochief.processing.Chain;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * One native coin purchase as the customer sees it; the answer of both {@code nativeCoin().buy()}
 * and {@code nativeCoin().order()}.
 *
 * <p>{@code txHash}, {@code transferFee}, {@code transferFeeUsd}, {@code coinPriceUsd},
 * {@code totalUsd}, {@code credits} and {@code coinUsd} are what the order was actually delivered
 * and billed, read back from the order rather than converted now. On an order nobody was charged
 * for (a {@code refused} one) only {@code txHash}, {@code totalUsd} and {@code credits} are
 * {@code null} - omitted, because a zero would read as "this was free". The remaining four are
 * always on the wire, and on a refusal that never got priced they arrive as {@code ""} or
 * {@code "0.00"} rather than being absent.
 *
 * <p>{@code settled} says the outcome is final either way; {@code needsAttention} says it is not
 * and that no retry will help - an order with {@code needsAttention} set must not be re-bought,
 * follow it with {@code nativeCoin().order(key)} until it settles.
 *
 * <p>On a failure {@code errorCode} is the stable machine code to branch on
 * ({@code INSUFFICIENT_LIQUIDITY}, {@code INSUFFICIENT_CREDITS}, ...) and {@code error} the
 * sanitised human sentence for logs - never the other way round. Both are {@code null} while
 * there is nothing to explain.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record NativeOrder(
        @JsonProperty("id") long id,
        @JsonProperty("idempotency_key") String idempotencyKey,
        @JsonProperty("status") String status,
        @JsonProperty("network") Chain network,
        @JsonProperty("receive_address") String receiveAddress,
        @JsonProperty("amount") String amount,
        @JsonProperty("tx_hash") String txHash,
        @JsonProperty("transfer_fee") String transferFee,
        @JsonProperty("transfer_fee_usd") String transferFeeUsd,
        @JsonProperty("coin_price_usd") String coinPriceUsd,
        @JsonProperty("total_usd") String totalUsd,
        @JsonProperty("credits") Long credits,
        @JsonProperty("coin_usd") String coinUsd,
        @JsonProperty("settled") boolean settled,
        @JsonProperty("needs_attention") boolean needsAttention,
        @JsonProperty("error_code") String errorCode,
        @JsonProperty("error") String error,
        @JsonProperty("created_at") String createdAt,
        @JsonProperty("delivered_at") String deliveredAt
) {}
