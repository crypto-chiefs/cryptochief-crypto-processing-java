package com.cryptochief.processing;

import com.cryptochief.processing.rsa.RsaDecrypt;
import com.cryptochief.processing.rsa.RsaKeyLoader;
import org.junit.jupiter.api.Test;

import javax.crypto.Cipher;
import javax.crypto.spec.OAEPParameterSpec;
import javax.crypto.spec.PSource;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.MGF1ParameterSpec;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RsaDecryptTest {

    @Test
    void oaepSha256RoundTrip() throws Exception {
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
        kpg.initialize(2048);
        KeyPair kp = kpg.generateKeyPair();
        RSAPrivateKey priv = (RSAPrivateKey) kp.getPrivate();
        RSAPublicKey pub = (RSAPublicKey) kp.getPublic();

        String plaintext = "0x" + "ab".repeat(32);
        Cipher cipher = Cipher.getInstance("RSA/ECB/OAEPWithSHA-256AndMGF1Padding");
        OAEPParameterSpec params = new OAEPParameterSpec(
                "SHA-256", "MGF1", MGF1ParameterSpec.SHA256, PSource.PSpecified.DEFAULT);
        cipher.init(Cipher.ENCRYPT_MODE, pub, params);
        String ct = Base64.getEncoder().encodeToString(
                cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8)));
        assertEquals(plaintext, RsaDecrypt.oaepSha256(priv, ct));
    }

    @Test
    void loaderParsesPkcs8Pem() throws Exception {
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
        kpg.initialize(2048);
        RSAPrivateKey priv = (RSAPrivateKey) kpg.generateKeyPair().getPrivate();
        String pkcs8 = Base64.getEncoder().encodeToString(priv.getEncoded());
        StringBuilder pem = new StringBuilder("-----BEGIN PRIVATE KEY-----\n");
        for (int i = 0; i < pkcs8.length(); i += 64) {
            pem.append(pkcs8, i, Math.min(i + 64, pkcs8.length())).append('\n');
        }
        pem.append("-----END PRIVATE KEY-----\n");
        RSAPrivateKey parsed = RsaKeyLoader.loadPrivateKeyFromPem(pem.toString());
        assertEquals(priv.getModulus(), parsed.getModulus());
    }
}
