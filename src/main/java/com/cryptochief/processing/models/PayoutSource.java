package com.cryptochief.processing.models;

import com.cryptochief.processing.Chain;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * One wallet a payout draws funds from. All components are optional.
 *
 * <p>{@code amountCrypto} is the amount taken from the wallet. {@code amount} is never
 * populated; read {@code amountCrypto}.
 *
 * <p>{@code txid}, {@code feePaid} and {@code feePaidFiat} are absent until the source has a
 * transaction. {@code confirmations} is its count, absent until the transaction is on chain.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record PayoutSource(
        @JsonProperty("address") String address,
        // Never populated - see the note on this record.
        @JsonProperty("amount") String amount,
        @JsonProperty("coin") String coin,
        @JsonProperty("network") Chain network,
        @JsonProperty("amount_crypto") String amountCrypto,
        @JsonProperty("amount_crypto_raw") String amountCryptoRaw,
        @JsonProperty("need_refuel") Boolean needRefuel,
        @JsonProperty("refuel_amount") String refuelAmount,
        @JsonProperty("refuel_amount_raw") String refuelAmountRaw,
        @JsonProperty("estimated_fee") String estimatedFee,
        @JsonProperty("estimated_fee_fiat") String estimatedFeeFiat,
        @JsonProperty("fee_paid") String feePaid,
        @JsonProperty("fee_paid_fiat") String feePaidFiat,
        @JsonProperty("txid") String txid,
        @JsonProperty("confirmations") Integer confirmations
) {
    /** The 0.8.0 constructor; the other components are {@code null}. */
    public PayoutSource(String address, String amount, String coin) {
        this(address, amount, coin, null, null, null, null, null, null, null, null, null, null, null, null);
    }
}
