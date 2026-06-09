package com.cryptochief.processing;

import com.cryptochief.processing.tron.TronAddress;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TronAddressTest {

    private static final String BASE58 = "TR7NHqjeKQxGTCi8q8ZY4pL8otSzgjLj6t";
    private static final String HEX = "0x41a614f803b6fd780986a42c78ec9c7f77e6ded13c";

    @Test
    void base58ToHex() {
        assertEquals(HEX, TronAddress.toHex(BASE58));
    }

    @Test
    void hexToBase58RoundTrips() {
        assertEquals(BASE58, TronAddress.fromHex(HEX));
    }

    @Test
    void twentyByteHexGetsPrefixed() {
        String twenty = HEX.substring(2 + 2); // strip 0x + 41
        assertEquals(BASE58, TronAddress.fromHex("0x" + twenty));
    }

    @Test
    void badChecksumRejected() {
        String tampered = BASE58.substring(0, BASE58.length() - 4) + "AAAA";
        assertThrows(IllegalArgumentException.class, () -> TronAddress.toHex(tampered));
    }
}
