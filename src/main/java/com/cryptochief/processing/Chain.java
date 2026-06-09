package com.cryptochief.processing;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Objects;

/** Chain code — the {@code network} / {@code chain} / {@code network_code} string used across the API. */
public final class Chain {

    public static final Chain ETH_MAINNET = new Chain("ETH_MAINNET");
    public static final Chain ETH_SEPOLIA = new Chain("ETH_SEPOLIA");
    public static final Chain BSC_MAINNET = new Chain("BSC_MAINNET");
    public static final Chain BSC_TESTNET = new Chain("BSC_TESTNET");
    public static final Chain POLYGON_MAINNET = new Chain("POLYGON_MAINNET");
    public static final Chain POLYGON_AMOY = new Chain("POLYGON_AMOY");
    public static final Chain ARBITRUM_ONE = new Chain("ARBITRUM_ONE");
    public static final Chain ARBITRUM_SEPOLIA = new Chain("ARBITRUM_SEPOLIA");
    public static final Chain OPTIMISM_MAINNET = new Chain("OPTIMISM_MAINNET");
    public static final Chain OPTIMISM_SEPOLIA = new Chain("OPTIMISM_SEPOLIA");
    public static final Chain AVAX_MAINNET = new Chain("AVAX_MAINNET");
    public static final Chain AVAX_TESTNET = new Chain("AVAX_TESTNET");

    public static final Chain BTC_MAINNET = new Chain("BTC_MAINNET");
    public static final Chain BTC_TESTNET_4 = new Chain("BTC_TESTNET_4");
    public static final Chain LITECOIN_MAINNET = new Chain("LITECOIN_MAINNET");
    public static final Chain BITCOIN_CASH_MAINNET = new Chain("BITCOIN_CASH_MAINNET");
    public static final Chain DOGECOIN_MAINNET = new Chain("DOGECOIN_MAINNET");

    public static final Chain TRON_MAINNET = new Chain("TRON_MAINNET");
    public static final Chain TRON_NILE = new Chain("TRON_NILE");

    public static final Chain SOLANA_MAINNET = new Chain("SOLANA_MAINNET");
    public static final Chain SOLANA_DEVNET = new Chain("SOLANA_DEVNET");

    public static final Chain TON_MAINNET = new Chain("TON_MAINNET");
    public static final Chain TON_TESTNET = new Chain("TON_TESTNET");

    public static final Chain XRP_MAINNET = new Chain("XRP_MAINNET");
    public static final Chain XRP_TESTNET = new Chain("XRP_TESTNET");

    private final String code;

    private Chain(String code) {
        this.code = Objects.requireNonNull(code, "code");
    }

    /** Construct a chain by its server-side code. Use for chains the SDK does not declare as constants. */
    @JsonCreator
    public static Chain of(String code) {
        return new Chain(code);
    }

    @JsonValue
    public String code() {
        return code;
    }

    public ChainFamily family() {
        return ChainFamily.of(this);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Chain other)) return false;
        return code.equals(other.code);
    }

    @Override
    public int hashCode() {
        return code.hashCode();
    }

    @Override
    public String toString() {
        return code;
    }
}
