package com.cryptochief.processing.models;

import com.cryptochief.processing.Chain;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Fee estimation without signing or broadcasting: a {@link SignTransactionRequest} transfer
 * ({@code native} or {@code token}) without {@code url_callback}. A {@code contract} estimate
 * is refused with {@code CONTRACT_ESTIMATE_UNSUPPORTED}.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record EstimateTransactionRequest(
        @JsonProperty("network") Chain network,
        @JsonProperty("from_address") String fromAddress,
        @JsonProperty("type") String type,
        @JsonProperty("to_address") String toAddress,
        @JsonProperty("value") String value,
        @JsonProperty("contract") String contract
) {
    /** A {@code native} transfer of {@code value} base units. */
    public static EstimateTransactionRequest of(Chain network, String fromAddress,
                                                String toAddress, String value) {
        return new EstimateTransactionRequest(network, fromAddress, TxType.NATIVE, toAddress, value, null);
    }

    /** A {@code token} transfer of {@code value} base units of the {@code contract} token. */
    public static EstimateTransactionRequest ofToken(Chain network, String fromAddress,
                                                     String toAddress, String value, String contract) {
        return new EstimateTransactionRequest(network, fromAddress, TxType.TOKEN, toAddress, value, contract);
    }
}
