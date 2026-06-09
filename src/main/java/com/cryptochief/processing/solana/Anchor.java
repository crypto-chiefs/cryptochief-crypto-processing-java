package com.cryptochief.processing.solana;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;

/** Anchor instruction encoder. */
public final class Anchor {

    private Anchor() {}

    public static byte[] discriminator(String method) {
        MessageDigest sha;
        try {
            sha = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
        byte[] sum = sha.digest(("global:" + method).getBytes(StandardCharsets.UTF_8));
        return Arrays.copyOfRange(sum, 0, 8);
    }

    public static byte[] encodeInstruction(String method, Borsh... args) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.writeBytes(discriminator(method));
        for (Borsh a : args) out.writeBytes(a.encode());
        return out.toByteArray();
    }
}
