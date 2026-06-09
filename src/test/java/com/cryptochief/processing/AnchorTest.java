package com.cryptochief.processing;

import com.cryptochief.processing.solana.Anchor;
import com.cryptochief.processing.solana.Borsh;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

class AnchorTest {

    @Test
    void discriminatorMatchesSha256Prefix() throws Exception {
        byte[] expected = Arrays.copyOfRange(
                MessageDigest.getInstance("SHA-256")
                        .digest("global:initialize".getBytes(StandardCharsets.UTF_8)),
                0, 8);
        assertArrayEquals(expected, Anchor.discriminator("initialize"));
    }

    @Test
    void instructionIsDiscriminatorPlusBorshArgs() {
        byte[] data = Anchor.encodeInstruction("transfer", Borsh.u64(1_000_000L));
        assertArrayEquals(Anchor.discriminator("transfer"), Arrays.copyOfRange(data, 0, 8));
        assertArrayEquals(new byte[]{0x40, 0x42, 0x0F, 0x00, 0x00, 0x00, 0x00, 0x00},
                Arrays.copyOfRange(data, 8, 16));
    }

    @Test
    void borshStringLayout() {
        byte[] out = Borsh.string("hi").encode();
        assertArrayEquals(new byte[]{0x02, 0x00, 0x00, 0x00, 0x68, 0x69}, out);
    }
}
