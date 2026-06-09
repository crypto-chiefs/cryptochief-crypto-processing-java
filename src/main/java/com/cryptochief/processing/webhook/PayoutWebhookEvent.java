package com.cryptochief.processing.webhook;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;

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
        @JsonProperty("created_at") String createdAt,
        @JsonProperty("completed_at") String completedAt,
        @JsonProperty("error_reason") String errorReason
) {}
