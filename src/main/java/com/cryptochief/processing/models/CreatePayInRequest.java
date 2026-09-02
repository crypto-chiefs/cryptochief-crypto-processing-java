package com.cryptochief.processing.models;

import com.cryptochief.processing.Asset;
import com.cryptochief.processing.AssetsPolicy;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * The body of {@code /v1/payments/order/create}.
 *
 * <p>{@code masterWalletAddress} pins the transit deposit wallet of THIS order to the given
 * master wallet of the project - the address the funds are swept to. The order's
 * asset/network chain family must match the master wallet's; a foreign or mismatched
 * address is rejected with 400.
 *
 * <p>{@code environment} constrains the asset the platform PICKS for this order to the real
 * chains or the test ones - a value of {@link Environment}. It changes nothing when
 * {@code asset} names a concrete network; it matters in fiat mode and when the network is
 * {@code ANY}, where the platform selects the asset and an unconstrained pick could put a
 * real payment on a test network. Null uses the project's own default.
 */
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
        @JsonProperty("asset") Asset asset,
        @JsonProperty("master_wallet_address") String masterWalletAddress,
        @JsonProperty("environment") String environment
) {

    /**
     * The order without the two newer fields, which the platform then resolves from the
     * project's own configuration. Kept so code written before they existed still
     * compiles.
     */
    public CreatePayInRequest(
            String orderId,
            String userId,
            String mode,
            String toAddress,
            Integer lifetimeSec,
            String urlCallback,
            String urlSuccess,
            String urlError,
            String additionalData,
            Integer accuracyPaymentPercent,
            String amountFiat,
            String currency,
            String courseSource,
            AssetsPolicy assets,
            String amountCrypto,
            Asset asset) {
        this(orderId, userId, mode, toAddress, lifetimeSec, urlCallback, urlSuccess,
                urlError, additionalData, accuracyPaymentPercent, amountFiat, currency,
                courseSource, assets, amountCrypto, asset, null, null);
    }

    /** The same order, pinned to a master wallet. */
    public CreatePayInRequest withMasterWallet(String masterWalletAddress) {
        return new CreatePayInRequest(orderId, userId, mode, toAddress, lifetimeSec,
                urlCallback, urlSuccess, urlError, additionalData, accuracyPaymentPercent,
                amountFiat, currency, courseSource, assets, amountCrypto, asset,
                masterWalletAddress, environment);
    }

    /** The same order, constrained to one environment. See {@link Environment}. */
    public CreatePayInRequest withEnvironment(String environment) {
        return new CreatePayInRequest(orderId, userId, mode, toAddress, lifetimeSec,
                urlCallback, urlSuccess, urlError, additionalData, accuracyPaymentPercent,
                amountFiat, currency, courseSource, assets, amountCrypto, asset,
                masterWalletAddress, environment);
    }
}
