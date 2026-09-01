package com.cryptochief.processing.models;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * The body of {@code /v1/wallets/label}. Both fields are required, and an empty
 * {@code label} is one of the values the endpoint takes rather than a missing one: it
 * clears the name.
 *
 * <p>Which is why null is turned into an empty string here instead of being left alone. The
 * canonical encoder drops nulls, so a null would take {@code label} off the wire entirely -
 * a malformed request, not the "this wallet has no name" the caller meant. The endpoint has
 * no third state to express: it always writes the value it is handed.
 *
 * <p>The opposite reading applies on {@link GenerateWalletRequest}, where an empty label is
 * normalised to null and stays off the wire - creation has a third state, "never named",
 * and this endpoint does not.
 */
public record SetLabelRequest(
        @JsonProperty("address") String address,
        @JsonProperty("label") String label
) {

    public SetLabelRequest {
        if (label == null) {
            label = "";
        }
    }
}
