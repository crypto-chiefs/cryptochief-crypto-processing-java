package com.cryptochief.processing.models;

import com.cryptochief.processing.Chain;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record EstimatePayoutResponse(
        @JsonProperty("network") Chain network,
        @JsonProperty("coin") String coin,
        @JsonProperty("amount") String amount,
        @JsonProperty("amount_to_receive") String amountToReceive,
        @JsonProperty("to_address") String toAddress,
        @JsonProperty("fee_info") PayoutFeeInfo feeInfo,
        @JsonProperty("sources") List<PayoutSource> sources,
        @JsonProperty("auto_convert_applied") boolean autoConvertApplied
) {}
