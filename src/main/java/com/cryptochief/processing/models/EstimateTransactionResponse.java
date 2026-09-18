package com.cryptochief.processing.models;

import com.cryptochief.processing.Chain;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Network fee estimate for a transaction that was neither signed nor broadcast.
 *
 * <p>{@code estimatedFee} is the network fee in the native coin, human-readable; {@code required}
 * is how much native coin the from-wallet must hold ({@code fee + value} for a {@code native}
 * transfer, {@code fee} for a {@code token} one). The {@code *Fiat} fields are the same amounts
 * in USD and read as an empty string when no rate is available.
 *
 * <p>The {@code feeExpected}/{@code feeLimit}/{@code energy}/{@code energyFee}/{@code bandwidthFee}/
 * {@code activationFee} breakdown is TRON-only; on every other network all six are {@code null}.
 * {@code energyFee + bandwidthFee + activationFee} add up to {@code estimatedFee} (gross, as if the
 * wallet burned TRX for everything). {@code feeExpected} is what the transfer is expected to cost
 * given the wallet's current energy pool (staked, delegated or rented) - an expectation, not a
 * guarantee: the pool may be spent before the transaction is broadcast. {@code feeLimit} is the
 * on-chain cap that would be written into the transaction. {@code activationFee} is present only
 * for a {@code native} transfer to an address that does not exist yet.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record EstimateTransactionResponse(
        @JsonProperty("network") Chain network,
        @JsonProperty("chain_family") String chainFamily,
        @JsonProperty("type") String type,
        @JsonProperty("from_address") String fromAddress,
        @JsonProperty("to_address") String toAddress,
        @JsonProperty("estimated_fee") String estimatedFee,
        @JsonProperty("estimated_fee_fiat") String estimatedFeeFiat,
        @JsonProperty("required") String required,
        @JsonProperty("required_fiat") String requiredFiat,
        @JsonProperty("fee_expected") String feeExpected,
        @JsonProperty("fee_limit") String feeLimit,
        @JsonProperty("energy") Long energy,
        @JsonProperty("energy_fee") String energyFee,
        @JsonProperty("bandwidth_fee") String bandwidthFee,
        @JsonProperty("activation_fee") String activationFee
) {}
