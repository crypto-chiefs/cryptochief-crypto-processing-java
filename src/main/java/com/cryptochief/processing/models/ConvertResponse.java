package com.cryptochief.processing.models;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ConvertResponse(
        @JsonProperty("amount_crypto") double amountCrypto,
        @JsonProperty("amount_fiat") double amountFiat,
        @JsonProperty("crypto") String crypto,
        @JsonProperty("crypto_to_usdt") double cryptoToUsdt,
        @JsonProperty("exchange") String exchange,
        @JsonProperty("fiat") String fiat,
        @JsonProperty("fiat_to_usd") double fiatToUsd,
        @JsonProperty("timestamp_crypto") long timestampCrypto,
        @JsonProperty("timestamp_fiat") long timestampFiat
) {}
