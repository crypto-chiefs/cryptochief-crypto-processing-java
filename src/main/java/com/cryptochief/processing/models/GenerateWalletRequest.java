package com.cryptochief.processing.models;

import com.cryptochief.processing.ChainFamily;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record GenerateWalletRequest(
        @JsonProperty("wallet_type") String walletType,
        @JsonProperty("chain_family") ChainFamily chainFamily,
        @JsonProperty("master_wallet_address") String masterWalletAddress,
        @JsonProperty("callback_url") String callbackUrl
) {}
