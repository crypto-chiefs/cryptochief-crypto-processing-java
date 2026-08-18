package com.cryptochief.processing.models;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/** Credits balance snapshot; 10,000,000 credits = 1 USD, {@code usdBalance} may be negative. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record CreditsBalance(
        @JsonProperty("credits_balance") long creditsBalance,
        @JsonProperty("usd_balance") String usdBalance,
        @JsonProperty("is_postpaid") boolean isPostpaid,
        @JsonProperty("debt_limit_credits") long debtLimitCredits,
        @JsonProperty("can_execute_gas_operations") boolean canExecuteGasOperations,
        @JsonProperty("gas_ops_min_credits") long gasOpsMinCredits,
        @JsonProperty("timestamp") String timestamp
) {}
