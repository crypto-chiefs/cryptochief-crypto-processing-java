package com.cryptochief.processing.rsa;

import com.cryptochief.processing.exceptions.ConfigurationException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPrivateKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** PEM RSA private key loader. Accepts PKCS#1 and PKCS#8. */
public final class RsaKeyLoader {

    private static final Pattern HEADER = Pattern.compile("-----BEGIN ([A-Z ]+)-----");

    private RsaKeyLoader() {}

    public static RSAPrivateKey loadPrivateKeyFromFile(String path) {
        try {
            return loadPrivateKeyFromPem(Files.readString(Path.of(path)));
        } catch (IOException e) {
            throw new ConfigurationException("cryptochief: RSA key: read " + path + ": " + e.getMessage(), e);
        }
    }

    public static RSAPrivateKey loadPrivateKeyFromPem(String pem) {
        String trimmed = pem.strip();
        Matcher m = HEADER.matcher(trimmed);
        if (!m.find()) {
            throw new ConfigurationException("cryptochief: RSA key: no PEM header found");
        }
        String label = m.group(1).trim();
        String body = trimmed
                .substring(trimmed.indexOf("-----BEGIN " + label + "-----") + ("-----BEGIN " + label + "-----").length());
        int endIdx = body.indexOf("-----END " + label + "-----");
        if (endIdx < 0) {
            throw new ConfigurationException("cryptochief: RSA key: no PEM footer found");
        }
        String base64 = body.substring(0, endIdx).replaceAll("\\s", "");
        byte[] raw;
        try {
            raw = Base64.getDecoder().decode(base64);
        } catch (IllegalArgumentException e) {
            throw new ConfigurationException("cryptochief: RSA key: bad base64", e);
        }
        byte[] der = switch (label.toUpperCase()) {
            case "PRIVATE KEY" -> raw;
            case "RSA PRIVATE KEY" -> pkcs1ToPkcs8(raw);
            default -> throw new ConfigurationException(
                    "cryptochief: RSA key: unexpected PEM label \"" + label + "\"");
        };
        try {
            return (RSAPrivateKey) KeyFactory.getInstance("RSA")
                    .generatePrivate(new PKCS8EncodedKeySpec(der));
        } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
            throw new ConfigurationException("cryptochief: RSA key: not a valid RSA private key", e);
        }
    }

    private static byte[] pkcs1ToPkcs8(byte[] pkcs1) {
        byte[] algId = new byte[]{
                0x30, 0x0D, 0x06, 0x09, 0x2A, (byte) 0x86, 0x48, (byte) 0x86,
                (byte) 0xF7, 0x0D, 0x01, 0x01, 0x01, 0x05, 0x00,
        };
        byte[] version = new byte[]{0x02, 0x01, 0x00};
        byte[] octet = derOctetString(pkcs1);
        byte[] body = concat(version, algId, octet);
        return derSequence(body);
    }

    private static byte[] derSequence(byte[] content) {
        return concat(new byte[]{0x30}, derLength(content.length), content);
    }

    private static byte[] derOctetString(byte[] content) {
        return concat(new byte[]{0x04}, derLength(content.length), content);
    }

    private static byte[] derLength(int len) {
        if (len < 0x80) return new byte[]{(byte) len};
        byte[] tmp = new byte[4];
        int n = 0;
        int v = len;
        while (v > 0) {
            tmp[3 - n] = (byte) (v & 0xFF);
            v >>>= 8;
            n++;
        }
        byte[] out = new byte[1 + n];
        out[0] = (byte) (0x80 | n);
        System.arraycopy(tmp, 4 - n, out, 1, n);
        return out;
    }

    private static byte[] concat(byte[]... parts) {
        int total = 0;
        for (byte[] p : parts) total += p.length;
        byte[] out = new byte[total];
        int offset = 0;
        for (byte[] p : parts) {
            System.arraycopy(p, 0, out, offset, p.length);
            offset += p.length;
        }
        return out;
    }
}
