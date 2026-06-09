package com.cryptochief.processing.solana;

import java.math.BigInteger;

/** Base58 without separators or version-byte handling. */
public final class Base58 {

    private static final String ALPHABET =
            "123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz";
    private static final int[] DECODE_TABLE = new int[128];

    static {
        for (int i = 0; i < DECODE_TABLE.length; i++) DECODE_TABLE[i] = -1;
        for (int i = 0; i < ALPHABET.length(); i++) DECODE_TABLE[ALPHABET.charAt(i)] = i;
    }

    private Base58() {}

    public static String encode(byte[] input) {
        if (input.length == 0) return "";
        int zeros = 0;
        while (zeros < input.length && input[zeros] == 0) zeros++;
        BigInteger num = new BigInteger(1, input);
        BigInteger base = BigInteger.valueOf(58);
        StringBuilder out = new StringBuilder();
        while (num.signum() > 0) {
            BigInteger[] dm = num.divideAndRemainder(base);
            out.append(ALPHABET.charAt(dm[1].intValue()));
            num = dm[0];
        }
        for (int i = 0; i < zeros; i++) out.append(ALPHABET.charAt(0));
        return out.reverse().toString();
    }

    public static byte[] decode(String input) {
        if (input.isEmpty()) throw new IllegalArgumentException("empty base58 string");
        int zeros = 0;
        while (zeros < input.length() && input.charAt(zeros) == ALPHABET.charAt(0)) zeros++;
        BigInteger num = BigInteger.ZERO;
        BigInteger base = BigInteger.valueOf(58);
        for (int i = 0; i < input.length(); i++) {
            char ch = input.charAt(i);
            int v = (ch < 128) ? DECODE_TABLE[ch] : -1;
            if (v < 0) throw new IllegalArgumentException("invalid base58 char '" + ch + "'");
            num = num.multiply(base).add(BigInteger.valueOf(v));
        }
        byte[] body;
        if (num.signum() == 0) {
            body = new byte[0];
        } else {
            byte[] raw = num.toByteArray();
            body = (raw[0] == 0 && raw.length > 1) ? java.util.Arrays.copyOfRange(raw, 1, raw.length) : raw;
        }
        byte[] out = new byte[zeros + body.length];
        System.arraycopy(body, 0, out, zeros, body.length);
        return out;
    }
}
