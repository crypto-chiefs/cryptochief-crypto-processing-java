package com.cryptochief.processing.models;

import com.cryptochief.processing.AssetsPolicy;
import com.cryptochief.processing.Chain;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ExecutePayoutRequest(
        @JsonProperty("order_id") String orderId,
        @JsonProperty("user_id") String userId,
        @JsonProperty("network") Chain network,
        @JsonProperty("coin") String coin,
        @JsonProperty("amount") String amount,
        @JsonProperty("to_address") String toAddress,
        @JsonProperty("url_callback") String urlCallback,
        @JsonProperty("from_addresses") List<String> fromAddresses,
        @JsonProperty("allow_multiple_sources") Boolean allowMultipleSources,
        @JsonProperty("auto_convert") Boolean autoConvert,
        @JsonProperty("auto_convert_policy") AssetsPolicy autoConvertPolicy,
        @JsonProperty("max_fee_amount_fiat") String maxFeeAmountFiat,
        @JsonProperty("memo") String memo
) {}
