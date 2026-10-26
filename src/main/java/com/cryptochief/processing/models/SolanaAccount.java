package com.cryptochief.processing.models;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

/** Solana account meta; {@code address} is the base58 account address. */
public record SolanaAccount(
        @JsonProperty("address") String address,
        @JsonProperty("is_signer") boolean isSigner,
        @JsonProperty("is_writable") boolean isWritable
) {
    /** @deprecated Use {@link #address()}. */
    @Deprecated
    @JsonIgnore
    public String pubkey() {
        return address;
    }
}
