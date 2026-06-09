package com.cryptochief.processing.ton;

import com.cryptochief.processing.evm.HexUtil;

import java.util.Arrays;
import java.util.Base64;
import java.util.Objects;

/** Parsed TON address: user-friendly EQ/UQ/kQ/0Q, or raw {@code workchain:hex}. */
public final class TonAddress {

    private final int workchain;
    private final byte[] hash;
    private final boolean bounceable;
    private final boolean testnet;

    public TonAddress(int workchain, byte[] hash, boolean bounceable, boolean testnet) {
        if (hash.length != 32) {
            throw new IllegalArgumentException("TON address hash must be 32 bytes, got " + hash.length);
        }
        if (workchain < -128 || workchain > 127) {
            throw new IllegalArgumentException("TON workchain " + workchain + " out of int8 range");
        }
        this.workchain = workchain;
        this.hash = hash.clone();
        this.bounceable = bounceable;
        this.testnet = testnet;
    }

    public int workchain() {
        return workchain;
    }

    public byte[] hash() {
        return hash.clone();
    }

    public boolean bounceable() {
        return bounceable;
    }

    public boolean testnet() {
        return testnet;
    }

    public String raw() {
        return workchain + ":" + HexUtil.toHex(hash);
    }

    @Override
    public String toString() {
        int tag = bounceable ? 0x11 : 0x51;
        if (testnet) tag |= 0x80;
        byte[] buf = new byte[36];
        buf[0] = (byte) tag;
        buf[1] = (byte) workchain;
        System.arraycopy(hash, 0, buf, 2, 32);
        int crc = crc16Xmodem(buf, 0, 34);
        buf[34] = (byte) ((crc >>> 8) & 0xFF);
        buf[35] = (byte) (crc & 0xFF);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(buf);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof TonAddress other)) return false;
        return workchain == other.workchain && bounceable == other.bounceable
                && testnet == other.testnet && Arrays.equals(hash, other.hash);
    }

    @Override
    public int hashCode() {
        return Objects.hash(workchain, Arrays.hashCode(hash), bounceable, testnet);
    }

    public static TonAddress parse(String input) {
        String s = input.strip();
        if (s.isEmpty()) throw new IllegalArgumentException("empty TON address");
        int colon = s.indexOf(':');
        return (colon > 0) ? parseRaw(s, colon) : parseUserFriendly(s);
    }

    private static TonAddress parseRaw(String s, int colon) {
        int wc;
        try {
            wc = Integer.parseInt(s.substring(0, colon));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("bad raw workchain \"" + s.substring(0, colon) + "\"", e);
        }
        String hashHex = s.substring(colon + 1);
        if (hashHex.length() != 64) {
            throw new IllegalArgumentException("hash hex length " + hashHex.length() + ", want 64");
        }
        return new TonAddress(wc, HexUtil.fromHex(hashHex), true, false);
    }

    private static TonAddress parseUserFriendly(String s) {
        if (s.length() != 48) {
            throw new IllegalArgumentException("user-friendly TON address length " + s.length() + ", want 48");
        }
        byte[] raw;
        try {
            raw = Base64.getUrlDecoder().decode(padBase64(s));
        } catch (IllegalArgumentException e1) {
            try {
                raw = Base64.getDecoder().decode(padBase64(s));
            } catch (IllegalArgumentException e2) {
                throw new IllegalArgumentException("TON address: base64 decode failed", e2);
            }
        }
        if (raw.length != 36) {
            throw new IllegalArgumentException("decoded TON address length " + raw.length + ", want 36");
        }
        int want = crc16Xmodem(raw, 0, 34);
        int got = ((raw[34] & 0xFF) << 8) | (raw[35] & 0xFF);
        if (want != got) {
            throw new IllegalArgumentException("TON address CRC mismatch");
        }
        int tag = raw[0] & 0xFF;
        return new TonAddress(
                raw[1],
                Arrays.copyOfRange(raw, 2, 34),
                (tag & 0x40) == 0,
                (tag & 0x80) != 0);
    }

    private static String padBase64(String s) {
        int mod = s.length() % 4;
        if (mod == 0) return s;
        return s + "=".repeat(4 - mod);
    }

    private static int crc16Xmodem(byte[] data, int off, int len) {
        int crc = 0;
        for (int i = off; i < off + len; i++) {
            crc ^= (data[i] & 0xFF) << 8;
            for (int j = 0; j < 8; j++) {
                crc = (crc & 0x8000) != 0 ? ((crc << 1) ^ 0x1021) : (crc << 1);
            }
            crc &= 0xFFFF;
        }
        return crc;
    }
}
