package com.cryptochief.processing.rsa;

import com.cryptochief.processing.exceptions.ConfigurationException;

import javax.crypto.Cipher;
import javax.crypto.spec.OAEPParameterSpec;
import javax.crypto.spec.PSource;
import java.nio.charset.StandardCharsets;
import java.security.interfaces.RSAPrivateKey;
import java.security.spec.MGF1ParameterSpec;
import java.util.Base64;

/** RSA-OAEP / SHA-256 decryption. */
public final class RsaDecrypt {

    private RsaDecrypt() {}

    public static String oaepSha256(RSAPrivateKey privateKey, String base64Ciphertext) {
        byte[] ct;
        try {
            ct = Base64.getDecoder().decode(base64Ciphertext);
        } catch (IllegalArgumentException e) {
            throw new ConfigurationException("cryptochief: RSA decrypt: bad base64", e);
        }
        try {
            Cipher cipher = Cipher.getInstance("RSA/ECB/OAEPWithSHA-256AndMGF1Padding");
            OAEPParameterSpec params = new OAEPParameterSpec(
                    "SHA-256", "MGF1", MGF1ParameterSpec.SHA256, PSource.PSpecified.DEFAULT);
            cipher.init(Cipher.DECRYPT_MODE, privateKey, params);
            return new String(cipher.doFinal(ct), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new ConfigurationException("cryptochief: RSA decrypt: " + e.getMessage(), e);
        }
    }
}
