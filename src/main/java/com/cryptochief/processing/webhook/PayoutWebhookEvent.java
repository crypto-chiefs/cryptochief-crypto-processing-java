package com.cryptochief.processing.webhook;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;

/**
 * {@code payout.paid} or {@code payout.system_fail}.
 *
 * <p>{@code payout.paid} is sent once every source reaches {@code requiredConfirmations}.
 * {@code confirmations} is the lowest count among {@code sources}; each item of
 * {@code sources} and {@code service_operations} has its own {@code confirmations}. All of them
 * are optional.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record PayoutWebhookEvent(
        @JsonProperty("event") String event,
        @JsonProperty("uuid") String uuid,
        @JsonProperty("order_id") String orderId,
        @JsonProperty("user_id") String userId,
        @JsonProperty("status") String status,
        @JsonProperty("amount_requested") String amountRequested,
        @JsonProperty("amount_to_receive") String amountToReceive,
        @JsonProperty("to_address") String toAddress,
        @JsonProperty("fee_info") JsonNode feeInfo,
        @JsonProperty("sources") JsonNode sources,
        @JsonProperty("service_operations") JsonNode serviceOperations,
        @JsonProperty("confirmations") Integer confirmations,
        @JsonProperty("required_confirmations") Integer requiredConfirmations,
        @JsonProperty("created_at") String createdAt,
        @JsonProperty("completed_at") String completedAt,
        @JsonProperty("error_reason") String errorReason
) {
    /** The 0.8.0 constructor; {@code confirmations} and {@code requiredConfirmations} are {@code null}. */
    public PayoutWebhookEvent(String event, String uuid, String orderId, String userId,
                              String status, String amountRequested, String amountToReceive,
                              String toAddress, JsonNode feeInfo, JsonNode sources,
                              JsonNode serviceOperations, String createdAt, String completedAt,
                              String errorReason) {
        this(event, uuid, orderId, userId, status, amountRequested, amountToReceive, toAddress,
                feeInfo, sources, serviceOperations, null, null, createdAt, completedAt,
                errorReason);
    }
}
