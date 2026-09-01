package com.cryptochief.processing.models;

import com.cryptochief.processing.ChainFamily;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * A wallet as the platform describes it - the shape returned by generate, info, list, and
 * by every endpoint that changes a wallet.
 *
 * <p>{@code masterWalletAddress}, {@code callbackUrl} and {@code label} are the three
 * fields that can be absent. The key is always present and carries null for "no such
 * value", never an empty string: a wallet with no name reads as {@code label() == null},
 * and {@link com.cryptochief.processing.services.WalletsService#setLabel} is what puts one
 * there or takes it away.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record Wallet(
        @JsonProperty("address") String address,
        @JsonProperty("chain_family") ChainFamily chainFamily,
        @JsonProperty("type") String type,
        @JsonProperty("wallet_type") String walletType,
        @JsonProperty("frozen") boolean frozen,
        @JsonProperty("master_wallet_address") String masterWalletAddress,
        @JsonProperty("callback_url") String callbackUrl,
        @JsonProperty("label") String label,
        @JsonProperty("private_key_encrypted") String privateKeyEncrypted,
        @JsonProperty("created_at") String createdAt,
        @JsonProperty("coins") List<WalletCoinBalance> coins,
        @JsonProperty("total_balance_usd") String totalBalanceUsd
) {}
