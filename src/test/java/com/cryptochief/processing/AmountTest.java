package com.cryptochief.processing;

import org.junit.jupiter.api.Test;

import java.math.BigInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AmountTest {

    @Test
    void toBase_humanToWei() {
        assertEquals(new BigInteger("1500000000000000000"), Amount.toBase("1.5", 18));
        assertEquals(new BigInteger("10000"), Amount.toBase("0.0001", 8));
        assertEquals(BigInteger.ZERO, Amount.toBase("0", 18));
        assertEquals(BigInteger.ONE, Amount.toBase("0.000000000000000001", 18));
    }

    @Test
    void toBase_truncatesExcessDecimals() {
        assertEquals(new BigInteger("12500000"), Amount.toBase("12.50000099", 6));
    }

    @Test
    void toBase_rejectsBadInput() {
        assertThrows(IllegalArgumentException.class, () -> Amount.toBase("-1", 18));
        assertThrows(IllegalArgumentException.class, () -> Amount.toBase("1e5", 18));
        assertThrows(IllegalArgumentException.class, () -> Amount.toBase("1.5E2", 18));
        assertThrows(IllegalArgumentException.class, () -> Amount.toBase("  ", 18));
    }

    @Test
    void fromBase_invertsToBase() {
        assertEquals("1.5", Amount.fromBase(new BigInteger("1500000000000000000"), 18));
        assertEquals("0", Amount.fromBase(BigInteger.ZERO, 18));
        assertEquals("0.0001", Amount.fromBase(new BigInteger("10000"), 8));
    }

    @Test
    void nanoTon() {
        assertEquals("50000000", Amount.nanoTon("0.05"));
        assertEquals("0", Amount.nanoTon("0"));
    }
}
