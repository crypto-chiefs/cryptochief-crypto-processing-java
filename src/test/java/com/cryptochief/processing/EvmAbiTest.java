package com.cryptochief.processing;

import com.cryptochief.processing.evm.EvmAbi;
import com.cryptochief.processing.evm.HexUtil;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EvmAbiTest {

    @Test
    void transferSelector() {
        byte[] sel = EvmAbi.selector("transfer(address,uint256)");
        assertEquals("a9059cbb", HexUtil.toHex(sel));
    }

    @Test
    void transferEncoding() {
        String data = EvmAbi.encodeCallHex("transfer(address,uint256)",
                "0x000000000000000000000000000000000000dead",
                new BigInteger("1000"));
        String expected = "0xa9059cbb"
                + "000000000000000000000000000000000000000000000000000000000000dead"
                + "00000000000000000000000000000000000000000000000000000000000003e8";
        assertEquals(expected, data);
    }

    @Test
    void dynamicBytesLengthPrefixedAndPadded() {
        byte[] data = EvmAbi.encodeCall("setData(bytes)",
                new byte[]{(byte) 0xDE, (byte) 0xAD, (byte) 0xBE, (byte) 0xEF});
        String hex = HexUtil.toHex(data);
        assertTrue(hex.startsWith(HexUtil.toHex(EvmAbi.selector("setData(bytes)"))));
        assertTrue(hex.contains("0000000000000000000000000000000000000000000000000000000000000020"));
        assertTrue(hex.contains("0000000000000000000000000000000000000000000000000000000000000004"));
        assertTrue(hex.contains("deadbeef" + "00".repeat(28)));
    }

    @Test
    void addressAcceptsTronBase58() {
        String data = EvmAbi.encodeCallHex("transfer(address,uint256)",
                "TR7NHqjeKQxGTCi8q8ZY4pL8otSzgjLj6t",
                BigInteger.ONE);
        assertTrue(data.contains("a614f803b6fd780986a42c78ec9c7f77e6ded13c"));
    }

    @Test
    void unsupportedTypeFails() {
        assertThrows(IllegalArgumentException.class, () -> EvmAbi.encodeCall("bad(decimal)"));
    }

    @Test
    void wrongArgCountFails() {
        assertThrows(IllegalArgumentException.class,
                () -> EvmAbi.encodeCall("transfer(address,uint256)", "0xdead"));
    }

    @Test
    void arrayOfStrings() {
        String data = EvmAbi.encodeCallHex(
                "swap(uint256,address[])",
                BigInteger.ONE,
                List.of("0x000000000000000000000000000000000000beef",
                        "0x000000000000000000000000000000000000feed"));
        assertTrue(data.contains("beef"));
        assertTrue(data.contains("feed"));
    }
}
