package com.cryptochief.processing.models;

import com.cryptochief.processing.AssetsPolicy;
import com.cryptochief.processing.Chain;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record EstimatePayoutRequest(
        @JsonProperty("network") Chain network,
        @JsonProperty("coin") String coin,
        @JsonProperty("amount") String amount,
        @JsonProperty("to_address") String toAddress,
        @JsonProperty("from_addresses") List<String> fromAddresses,
        @JsonProperty("allow_multiple_sources") Boolean allowMultipleSources,
        @JsonProperty("auto_convert") Boolean autoConvert,
        @JsonProperty("auto_convert_policy") AssetsPolicy autoConvertPolicy,
        @JsonProperty("max_fee_amount_fiat") String maxFeeAmountFiat,
        @JsonProperty("memo") String memo
) {
    public static EstimatePayoutRequest of(Chain network, String coin, String amount, String toAddress) {
        return new EstimatePayoutRequest(network, coin, amount, toAddress, null, null, null, null, null, null);
    }
}
