package com.cryptochief.processing;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChainTest {

    @Test
    void knownChainMapsToFamily() {
        assertEquals(ChainFamily.EVM, Chain.ETH_MAINNET.family());
        assertEquals(ChainFamily.TON, Chain.TON_MAINNET.family());
        assertEquals(ChainFamily.SOLANA, Chain.SOLANA_MAINNET.family());
    }

    @Test
    void unknownChainHasNullFamily() {
        assertNull(Chain.of("FUTURE_CHAIN").family());
    }

    @Test
    void contractFamiliesFlag() {
        assertTrue(ChainFamily.EVM.supportsContractCalls());
        assertTrue(ChainFamily.TRON.supportsContractCalls());
        assertTrue(ChainFamily.SOLANA.supportsContractCalls());
        assertTrue(ChainFamily.TON.supportsContractCalls());
        assertFalse(ChainFamily.XRP_LEDGER.supportsContractCalls());
        assertFalse(ChainFamily.BTC_UTXO.supportsContractCalls());
    }
}
