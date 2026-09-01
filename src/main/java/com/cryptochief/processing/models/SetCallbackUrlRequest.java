package com.cryptochief.processing.models;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * The body of {@code /v1/wallets/callback-url}. Both fields are required, and an empty
 * {@code callbackUrl} is one of the values the endpoint takes rather than a missing one:
 * it clears the webhook.
 *
 * <p>Which is why null is turned into an empty string here instead of being left alone. The
 * canonical encoder drops nulls, so a null would take {@code callback_url} off the wire
 * entirely - a malformed request, not the "stop announcing deposits" the caller meant. This
 * endpoint has no third state to express: it always writes the value it is handed.
 */
public record SetCallbackUrlRequest(
        @JsonProperty("address") String address,
        @JsonProperty("callback_url") String callbackUrl
) {

    public SetCallbackUrlRequest {
        if (callbackUrl == null) {
            callbackUrl = "";
        }
    }
}
