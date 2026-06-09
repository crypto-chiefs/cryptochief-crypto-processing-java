package com.cryptochief.processing;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Protocol family for a {@link Chain} — the {@code chain_family} value in API responses. */
public final class ChainFamily {

    public static final ChainFamily EVM = new ChainFamily("EVM");
    public static final ChainFamily TRON = new ChainFamily("TRON");
    public static final ChainFamily SOLANA = new ChainFamily("SOLANA");
    public static final ChainFamily XRP_LEDGER = new ChainFamily("XRP_LEDGER");
    public static final ChainFamily TON = new ChainFamily("TON");
    public static final ChainFamily BTC_UTXO = new ChainFamily("BTC_UTXO");
    public static final ChainFamily BTC_UTXO_TESTNET = new ChainFamily("BTC_UTXO_TESTNET");
    public static final ChainFamily DOGECOIN_UTXO = new ChainFamily("DOGECOIN_UTXO");
    public static final ChainFamily BTC_CASH_UTXO = new ChainFamily("BTC_CASH_UTXO");
    public static final ChainFamily LITECOIN_UTXO = new ChainFamily("LITECOIN_UTXO");

    private static final Set<ChainFamily> CONTRACT_FAMILIES = Set.of(EVM, TRON, SOLANA, TON);

    private static final Map<Chain, ChainFamily> CHAIN_TO_FAMILY = Map.<Chain, ChainFamily>ofEntries(
            Map.entry(Chain.ETH_MAINNET, EVM),
            Map.entry(Chain.ETH_SEPOLIA, EVM),
            Map.entry(Chain.BSC_MAINNET, EVM),
            Map.entry(Chain.BSC_TESTNET, EVM),
            Map.entry(Chain.POLYGON_MAINNET, EVM),
            Map.entry(Chain.POLYGON_AMOY, EVM),
            Map.entry(Chain.ARBITRUM_ONE, EVM),
            Map.entry(Chain.ARBITRUM_SEPOLIA, EVM),
            Map.entry(Chain.OPTIMISM_MAINNET, EVM),
            Map.entry(Chain.OPTIMISM_SEPOLIA, EVM),
            Map.entry(Chain.AVAX_MAINNET, EVM),
            Map.entry(Chain.AVAX_TESTNET, EVM),
            Map.entry(Chain.BTC_MAINNET, BTC_UTXO),
            Map.entry(Chain.BTC_TESTNET_4, BTC_UTXO_TESTNET),
            Map.entry(Chain.LITECOIN_MAINNET, LITECOIN_UTXO),
            Map.entry(Chain.BITCOIN_CASH_MAINNET, BTC_CASH_UTXO),
            Map.entry(Chain.DOGECOIN_MAINNET, DOGECOIN_UTXO),
            Map.entry(Chain.TRON_MAINNET, TRON),
            Map.entry(Chain.TRON_NILE, TRON),
            Map.entry(Chain.SOLANA_MAINNET, SOLANA),
            Map.entry(Chain.SOLANA_DEVNET, SOLANA),
            Map.entry(Chain.TON_MAINNET, TON),
            Map.entry(Chain.TON_TESTNET, TON),
            Map.entry(Chain.XRP_MAINNET, XRP_LEDGER),
            Map.entry(Chain.XRP_TESTNET, XRP_LEDGER)
    );

    private final String code;

    private ChainFamily(String code) {
        this.code = Objects.requireNonNull(code, "code");
    }

    @JsonCreator
    public static ChainFamily ofCode(String code) {
        return new ChainFamily(code);
    }

    /** Family for a known chain. Returns {@code null} for unknown codes. */
    public static ChainFamily of(Chain chain) {
        return CHAIN_TO_FAMILY.get(chain);
    }

    @JsonValue
    public String code() {
        return code;
    }

    /** True for EVM, TRON, Solana, TON. */
    public boolean supportsContractCalls() {
        return CONTRACT_FAMILIES.contains(this);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ChainFamily other)) return false;
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
