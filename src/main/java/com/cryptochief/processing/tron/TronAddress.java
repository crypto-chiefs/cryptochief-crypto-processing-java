package com.cryptochief.processing.tron;

import com.cryptochief.processing.evm.HexUtil;
import com.cryptochief.processing.solana.Base58;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;

/** TRON address conversions between base58 ({@code T…}) and 0x41-prefixed hex. */
public final class TronAddress {

    private TronAddress() {}

    public static String toHex(String base58Address) {
        byte[] decoded = Base58.decode(base58Address.strip());
        if (decoded.length != 25) {
            throw new IllegalArgumentException("TRON address: decoded length " + decoded.length + ", want 25");
        }
        byte[] payload = Arrays.copyOfRange(decoded, 0, 21);
        byte[] sum = Arrays.copyOfRange(decoded, 21, 25);
        if (payload[0] != 0x41) {
            throw new IllegalArgumentException(
                    "TRON address: leading byte 0x" + Integer.toHexString(payload[0] & 0xFF) + ", want 0x41");
        }
        byte[] expected = Arrays.copyOfRange(sha256d(payload), 0, 4);
        if (!Arrays.equals(expected, sum)) {
            throw new IllegalArgumentException("TRON address: checksum mismatch");
        }
        return "0x" + HexUtil.toHex(payload);
    }

    public static String fromHex(String hexAddress) {
        String trimmed = hexAddress.strip();
        if (trimmed.startsWith("0x") || trimmed.startsWith("0X")) trimmed = trimmed.substring(2);
        byte[] raw = HexUtil.fromHex(trimmed);
        byte[] payload;
        if (raw.length == 20) {
            payload = new byte[21];
            payload[0] = 0x41;
            System.arraycopy(raw, 0, payload, 1, 20);
        } else if (raw.length == 21) {
            if (raw[0] != 0x41) {
                throw new IllegalArgumentException(
                        "TRON address: 21-byte input must start with 0x41, got 0x" + Integer.toHexString(raw[0] & 0xFF));
            }
            payload = raw;
        } else {
            throw new IllegalArgumentException("TRON address: want 20- or 21-byte hex, got " + raw.length + " bytes");
        }
        byte[] sum = Arrays.copyOfRange(sha256d(payload), 0, 4);
        byte[] combined = new byte[payload.length + 4];
        System.arraycopy(payload, 0, combined, 0, payload.length);
        System.arraycopy(sum, 0, combined, payload.length, 4);
        return Base58.encode(combined);
    }

    private static byte[] sha256d(byte[] data) {
        MessageDigest sha;
        try {
            sha = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
        byte[] first = sha.digest(data);
        sha.reset();
        return sha.digest(first);
    }
}
