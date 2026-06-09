package com.cryptochief.processing.models;

import com.cryptochief.processing.Asset;
import com.cryptochief.processing.AssetsPolicy;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record CreatePayInRequest(
        @JsonProperty("order_id") String orderId,
        @JsonProperty("user_id") String userId,
        @JsonProperty("mode") String mode,
        @JsonProperty("to_address") String toAddress,
        @JsonProperty("lifetime_sec") Integer lifetimeSec,
        @JsonProperty("url_callback") String urlCallback,
        @JsonProperty("url_success") String urlSuccess,
        @JsonProperty("url_error") String urlError,
        @JsonProperty("additional_data") String additionalData,
        @JsonProperty("accuracy_payment_percent") Integer accuracyPaymentPercent,
        @JsonProperty("amount_fiat") String amountFiat,
        @JsonProperty("currency") String currency,
        @JsonProperty("course_source") String courseSource,
        @JsonProperty("assets") AssetsPolicy assets,
        @JsonProperty("amount_crypto") String amountCrypto,
        @JsonProperty("asset") Asset asset
) {}
