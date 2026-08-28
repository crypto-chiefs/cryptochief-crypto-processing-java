package com.cryptochief.processing.models;

import com.cryptochief.processing.Chain;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * The body of {@code /v1/sweeps/settings}. A null address asks for the project's own
 * default rather than any wallet's policy.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record SweepSettingsQuery(
        @JsonProperty("address") String address,
        @JsonProperty("network_code") Chain networkCode
) {

    public static SweepSettingsQuery projectDefault() {
        return new SweepSettingsQuery(null, null);
    }

    public static SweepSettingsQuery forAddress(String address) {
        return new SweepSettingsQuery(address, null);
    }
}
