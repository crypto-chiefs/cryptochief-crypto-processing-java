package com.cryptochief.processing.models;

import com.cryptochief.processing.Chain;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * One payout, as execute, info and history return it.
 *
 * <p>{@code confirmations} is the lowest count among {@code sources}, absent until a source has
 * a transaction. {@code requiredConfirmations} is the finality depth of the network, optional.
 * The payout stays {@code confirm_check} until every source reaches
 * {@code requiredConfirmations}, then becomes {@link PayoutStatus#PAID}.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record PayoutInfo(
        @JsonProperty("uuid") String uuid,
        @JsonProperty("order_id") String orderId,
        @JsonProperty("status") String status,
        @JsonProperty("network") Chain network,
        @JsonProperty("coin") String coin,
        @JsonProperty("amount") String amount,
        @JsonProperty("to_address") String toAddress,
        @JsonProperty("txid") String txid,
        @JsonProperty("sources") List<PayoutSource> sources,
        @JsonProperty("service_operations") List<PayoutServiceOperation> serviceOperations,
        @JsonProperty("confirmations") Integer confirmations,
        @JsonProperty("required_confirmations") Integer requiredConfirmations,
        @JsonProperty("url_callback") String urlCallback,
        @JsonProperty("created_at") String createdAt,
        @JsonProperty("updated_at") String updatedAt,
        @JsonProperty("error") String error
) {
    /** The 0.8.0 constructor; {@code serviceOperations} and the counts are {@code null}. */
    public PayoutInfo(String uuid, String orderId, String status, Chain network, String coin,
                      String amount, String toAddress, String txid, List<PayoutSource> sources,
                      String urlCallback, String createdAt, String updatedAt, String error) {
        this(uuid, orderId, status, network, coin, amount, toAddress, txid, sources,
                null, null, null, urlCallback, createdAt, updatedAt, error);
    }

    public boolean isTerminal() {
        return PayoutStatus.TERMINAL.contains(status);
    }

    public boolean succeeded() {
        return PayoutStatus.PAID.equals(status);
    }
}
