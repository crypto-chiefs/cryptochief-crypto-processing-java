package com.cryptochief.processing.models;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/** The body the platform sent. {@code bytes} is the whole size even when {@code body} was cut. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record WebhookPayload(
        @JsonProperty("body") String body,
        @JsonProperty("bytes") int bytes,
        @JsonProperty("truncated") boolean truncated
) {}
