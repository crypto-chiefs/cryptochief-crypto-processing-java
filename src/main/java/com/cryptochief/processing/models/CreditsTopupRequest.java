package com.cryptochief.processing.models;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/** Topup invoice request; {@code amount} is a positive USD-pegged decimal, max 100000. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record CreditsTopupRequest(
        @JsonProperty("amount") String amount,
        @JsonProperty("currency") String currency,
        @JsonProperty("url_success") String urlSuccess,
        @JsonProperty("url_error") String urlError
) {
    public static CreditsTopupRequest of(String amount, String currency) {
        return new CreditsTopupRequest(amount, currency, null, null);
    }
}
