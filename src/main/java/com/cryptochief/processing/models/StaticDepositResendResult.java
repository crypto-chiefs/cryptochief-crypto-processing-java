package com.cryptochief.processing.models;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * The resend of a static deposit's webhook. {@code deliveries} has one entry - the newest
 * delivery for the deposit - kept as a list so the shape matches the white-label platform,
 * which may requeue several.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record StaticDepositResendResult(
        @JsonProperty("uuid") String uuid,
        @JsonProperty("deliveries") List<WebhookResendResult> deliveries,
        @JsonProperty("queued") int queued,
        @JsonProperty("total") int total
) {
    public StaticDepositResendResult {
        deliveries = deliveries == null ? List.of() : List.copyOf(deliveries);
    }
}
