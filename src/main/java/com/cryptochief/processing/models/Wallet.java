package com.cryptochief.processing.models;

import com.cryptochief.processing.ChainFamily;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record Wallet(
        @JsonProperty("address") String address,
        @JsonProperty("chain_family") ChainFamily chainFamily,
        @JsonProperty("type") String type,
        @JsonProperty("wallet_type") String walletType,
        @JsonProperty("frozen") boolean frozen,
        @JsonProperty("master_wallet_address") String masterWalletAddress,
        @JsonProperty("callback_url") String callbackUrl,
        @JsonProperty("private_key_encrypted") String privateKeyEncrypted,
        @JsonProperty("created_at") String createdAt,
        @JsonProperty("coins") List<WalletCoinBalance> coins,
        @JsonProperty("total_balance_usd") String totalBalanceUsd
) {}
