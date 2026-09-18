package com.cryptochief.processing.models;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * One energy rental as the customer sees it; the answer of both {@code energy().rent()} and
 * {@code energy().order()}.
 *
 * <p>{@code priceUsd}, {@code credits} and {@code trxUsd} are what the order was actually billed,
 * read back from the order rather than converted now - and all three are {@code null} on an order
 * nobody was charged for (a {@code refused} one), rather than zero: a zero would read as
 * "this was free".
 *
 * <p>{@code settled} says the outcome is final either way; {@code needsAttention} says it is not
 * and that no retry will help - an order with {@code needsAttention} set must not be re-bought,
 * follow it with {@code energy().order(key)} until it settles.
 *
 * <p>On a failure {@code errorCode} is the stable machine code to branch on
 * ({@code SUPPLIER_REFUSED}, {@code INSUFFICIENT_CREDITS}, ...) and {@code error} the sanitised
 * human sentence for logs - never the other way round. Both are {@code null} while there is
 * nothing to explain.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record EnergyOrder(
        @JsonProperty("id") long id,
        @JsonProperty("idempotency_key") String idempotencyKey,
        @JsonProperty("status") String status,
        @JsonProperty("receive_address") String receiveAddress,
        @JsonProperty("energy") long energy,
        @JsonProperty("duration_sec") long durationSec,
        @JsonProperty("price_sun") long priceSun,
        @JsonProperty("price_trx") String priceTrx,
        @JsonProperty("price_usd") String priceUsd,
        @JsonProperty("credits") Long credits,
        @JsonProperty("trx_usd") String trxUsd,
        @JsonProperty("delivered_energy") Long deliveredEnergy,
        @JsonProperty("settled") boolean settled,
        @JsonProperty("needs_attention") boolean needsAttention,
        @JsonProperty("error_code") String errorCode,
        @JsonProperty("error") String error,
        @JsonProperty("created_at") String createdAt,
        @JsonProperty("delivered_at") String deliveredAt
) {}
