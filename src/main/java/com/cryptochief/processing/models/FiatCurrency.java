package com.cryptochief.processing.models;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * One fiat currency the platform can price in: the ISO code and its English name.
 *
 * <p>These are the codes {@code ConvertRequest} accepts and the ones a pay-in may name in
 * {@code currency}. Availability here is about display and rate calculation only.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record FiatCurrency(
        @JsonProperty("code") String code,
        @JsonProperty("name") String name
) {}
