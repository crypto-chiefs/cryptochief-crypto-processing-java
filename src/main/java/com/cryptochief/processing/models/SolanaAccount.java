package com.cryptochief.processing.models;

import com.fasterxml.jackson.annotation.JsonProperty;

public record SolanaAccount(
        @JsonProperty("pubkey") String pubkey,
        @JsonProperty("is_signer") boolean isSigner,
        @JsonProperty("is_writable") boolean isWritable
) {}
