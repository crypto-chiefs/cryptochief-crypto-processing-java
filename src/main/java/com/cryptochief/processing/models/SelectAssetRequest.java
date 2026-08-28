package com.cryptochief.processing.models;

import com.cryptochief.processing.Chain;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * The body of {@code /v1/payments/order/select-asset}.
 *
 * <p>{@code masterWalletAddress} pins the order's transit deposit wallet to the given
 * project master wallet; see {@link CreatePayInRequest}. A value here overrides one
 * supplied at order create.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record SelectAssetRequest(
        @JsonProperty("uuid") String uuid,
        @JsonProperty("coin") String coin,
        @JsonProperty("network") Chain network,
        @JsonProperty("master_wallet_address") String masterWalletAddress
) {

    /** Without a master wallet: the project's own configuration decides. */
    public SelectAssetRequest(String uuid, String coin, Chain network) {
        this(uuid, coin, network, null);
    }
}
