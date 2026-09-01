package com.cryptochief.processing.models;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * The body of {@code /v1/wallets/rebind-master}. Both fields are required: the wallet being
 * re-pointed, and the master wallet it should settle to from now on.
 */
public record RebindMasterRequest(
        @JsonProperty("address") String address,
        @JsonProperty("master_wallet_address") String masterWalletAddress
) {}
