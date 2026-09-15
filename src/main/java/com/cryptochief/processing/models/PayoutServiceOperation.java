package com.cryptochief.processing.models;

import com.cryptochief.processing.Chain;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * A transaction the platform sends to make a payout possible, such as a {@code gas_refuel}
 * topping up a source wallet.
 *
 * <p>{@code feePaid}, {@code feePaidFiat}, {@code txid} and {@code confirmations} are optional.
 * {@code confirmations} is absent until the transaction is on chain.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record PayoutServiceOperation(
        @JsonProperty("type") String type,
        @JsonProperty("context") String context,
        @JsonProperty("status") String status,
        @JsonProperty("network") Chain network,
        @JsonProperty("coin") String coin,
        @JsonProperty("amount_native") String amountNative,
        @JsonProperty("amount_native_raw") String amountNativeRaw,
        @JsonProperty("from_address") String fromAddress,
        @JsonProperty("to_address") String toAddress,
        @JsonProperty("estimated_fee") String estimatedFee,
        @JsonProperty("estimated_fee_fiat") String estimatedFeeFiat,
        @JsonProperty("fee_paid") String feePaid,
        @JsonProperty("fee_paid_fiat") String feePaidFiat,
        @JsonProperty("txid") String txid,
        @JsonProperty("confirmations") Integer confirmations
) {}
