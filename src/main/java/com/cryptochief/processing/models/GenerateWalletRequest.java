package com.cryptochief.processing.models;

import com.cryptochief.processing.ChainFamily;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * The body of {@code /v1/wallets/generate}.
 *
 * <p>{@code masterWalletAddress} names the master a transit or static wallet settles to.
 * {@code callbackUrl} is the deposit webhook of a static wallet and belongs to that type
 * only; {@link com.cryptochief.processing.services.WalletsService#setCallbackUrl} changes
 * it afterwards.
 *
 * <p>{@code label} is a human-readable name for the wallet - at most 255 characters,
 * stored and never interpreted - and applies to every wallet type, master and transit as
 * much as static; {@link com.cryptochief.processing.services.WalletsService#setLabel}
 * changes it afterwards. Null leaves the wallet unnamed and keeps the field off the wire;
 * an empty string is normalised to null for the same reason, because it would be a name the
 * platform has to store rather than the absence of one.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record GenerateWalletRequest(
        @JsonProperty("wallet_type") String walletType,
        @JsonProperty("chain_family") ChainFamily chainFamily,
        @JsonProperty("master_wallet_address") String masterWalletAddress,
        @JsonProperty("callback_url") String callbackUrl,
        @JsonProperty("label") String label
) {

    public GenerateWalletRequest {
        if (label != null && label.isEmpty()) {
            label = null;
        }
    }

    /**
     * The request without a label, which leaves the wallet unnamed. Kept so code written
     * before labels existed still compiles.
     */
    public GenerateWalletRequest(
            String walletType,
            ChainFamily chainFamily,
            String masterWalletAddress,
            String callbackUrl) {
        this(walletType, chainFamily, masterWalletAddress, callbackUrl, null);
    }

    /** The same request, with a name for the wallet. */
    public GenerateWalletRequest withLabel(String label) {
        return new GenerateWalletRequest(
                walletType, chainFamily, masterWalletAddress, callbackUrl, label);
    }
}
