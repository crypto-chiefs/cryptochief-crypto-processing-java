package com.cryptochief.processing;

import java.math.BigInteger;

/** Conversion between human decimal strings and integer base units. */
public final class Amount {

    private Amount() {}

    /**
     * {@code Amount.toBase("1.5", 18)} returns {@code BigInteger("1500000000000000000")}.
     * Negative values and scientific notation throw. Sub-base-unit precision is truncated.
     */
    public static BigInteger toBase(String human, int decimals) {
        if (decimals < 0) {
            throw new IllegalArgumentException("decimals must be >= 0, got " + decimals);
        }
        String trimmed = human.strip();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException("amount is empty");
        }
        if (trimmed.indexOf('e') >= 0 || trimmed.indexOf('E') >= 0) {
            throw new IllegalArgumentException("scientific notation not allowed: \"" + human + "\"");
        }
        if (trimmed.startsWith("-")) {
            throw new IllegalArgumentException("negative amount not allowed: \"" + human + "\"");
        }

        int dot = trimmed.indexOf('.');
        String intPart;
        String fracPart;
        if (dot < 0) {
            intPart = trimmed;
            fracPart = "";
        } else {
            intPart = trimmed.substring(0, dot).isEmpty() ? "0" : trimmed.substring(0, dot);
            fracPart = trimmed.substring(dot + 1);
            if (fracPart.isEmpty()) {
                throw new IllegalArgumentException("amount has trailing dot: \"" + human + "\"");
            }
        }
        requireAllDigits(intPart, "integer part");
        requireAllDigits(fracPart, "fractional part");

        if (fracPart.length() > decimals) {
            fracPart = fracPart.substring(0, decimals);
        } else {
            StringBuilder pad = new StringBuilder(fracPart);
            while (pad.length() < decimals) pad.append('0');
            fracPart = pad.toString();
        }
        String combined = (intPart + fracPart).replaceFirst("^0+", "");
        if (combined.isEmpty()) combined = "0";
        return new BigInteger(combined);
    }

    /** {@code Amount.fromBase(BigInteger("1500000000000000000"), 18)} returns {@code "1.5"}. */
    public static String fromBase(BigInteger base, int decimals) {
        if (decimals <= 0) return base.abs().toString();
        String abs = base.abs().toString();
        StringBuilder padded = new StringBuilder(abs);
        while (padded.length() < decimals + 1) padded.insert(0, '0');
        int cut = padded.length() - decimals;
        String intPart = padded.substring(0, cut);
        String fracPart = padded.substring(cut).replaceFirst("0+$", "");
        return fracPart.isEmpty() ? intPart : intPart + "." + fracPart;
    }

    /** Decimal nanoTON string for {@code humanTon} — {@code nanoTon("0.05")} returns {@code "50000000"}. */
    public static String nanoTon(String humanTon) {
        return toBase(humanTon, 9).toString();
    }

    private static void requireAllDigits(String s, String name) {
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c < '0' || c > '9') {
                throw new IllegalArgumentException("invalid " + name + ": \"" + s + "\"");
            }
        }
    }
}
