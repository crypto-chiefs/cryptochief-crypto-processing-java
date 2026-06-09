package com.cryptochief.processing.webhook;

import com.cryptochief.processing.Chain;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record StaticDepositWebhookEvent(
        @JsonProperty("event") String event,
        @JsonProperty("uuid") String uuid,
        @JsonProperty("status") String status,
        @JsonProperty("network") Chain network,
        @JsonProperty("chain_family") String chainFamily,
        @JsonProperty("coin") String coin,
        @JsonProperty("contract") String contract,
        @JsonProperty("decimals") int decimals,
        @JsonProperty("to_address") String toAddress,
        @JsonProperty("from_address") String fromAddress,
        @JsonProperty("tx_hash") String txHash,
        @JsonProperty("amount") String amount,
        @JsonProperty("amount_fiat") String amountFiat,
        @JsonProperty("confirmations") int confirmations,
        @JsonProperty("required_confirmations") int requiredConfirmations,
        @JsonProperty("found_in_mempool") boolean foundInMempool,
        @JsonProperty("log_type") String logType,
        @JsonProperty("block_number") Long blockNumber,
        @JsonProperty("created_at") String createdAt,
        @JsonProperty("updated_at") String updatedAt,
        @JsonProperty("confirmed_at") String confirmedAt,
        @JsonProperty("paid_at") String paidAt
) {}
