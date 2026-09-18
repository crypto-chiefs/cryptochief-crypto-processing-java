package com.cryptochief.processing.models;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * A price the energy service stands behind until {@code expiresAt}; free to request.
 *
 * <p>Amounts come in three units of the same price: {@code priceSun} is the integer in SUN and
 * is authoritative, {@code priceTrx} is the human-readable decimal string, and {@code credits} is
 * what the rent will actually take from the credits balance - compare it against
 * {@code credits().balance().creditsBalance()} before ordering. {@code priceUsd}, {@code credits}
 * and {@code trxUsd} (the rate the conversion was made at) are {@code null} when no TRX/USD rate
 * is available; the SUN and TRX figures always are.
 *
 * <p>The {@code burn*} fields are what the same transfer would cost paying the chain directly,
 * published so the {@code saving*} figures are checkable rather than claimed. {@code recipientState}
 * explains the energy figure: an address that already holds the token needs about half as much as
 * one that does not.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record EnergyQuote(
        @JsonProperty("ref") String ref,
        @JsonProperty("receive_address") String receiveAddress,
        @JsonProperty("energy") long energy,
        @JsonProperty("duration_sec") long durationSec,
        @JsonProperty("price_sun") long priceSun,
        @JsonProperty("price_trx") String priceTrx,
        @JsonProperty("price_usd") String priceUsd,
        @JsonProperty("credits") Long credits,
        @JsonProperty("trx_usd") String trxUsd,
        @JsonProperty("recipient_state") String recipientState,
        @JsonProperty("burn_price_sun") long burnPriceSun,
        @JsonProperty("burn_price_trx") String burnPriceTrx,
        @JsonProperty("burn_price_usd") String burnPriceUsd,
        @JsonProperty("burn_price_credits") Long burnPriceCredits,
        @JsonProperty("saving_trx") String savingTrx,
        @JsonProperty("saving_usd") String savingUsd,
        @JsonProperty("saving_credits") Long savingCredits,
        @JsonProperty("expires_at") String expiresAt,
        @JsonProperty("expires_in_sec") long expiresInSec
) {}
