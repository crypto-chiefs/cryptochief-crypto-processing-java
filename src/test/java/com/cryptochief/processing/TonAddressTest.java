package com.cryptochief.processing;

import com.cryptochief.processing.ton.TonAddress;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TonAddressTest {

    private static final String USER_FRIENDLY = "EQCxE6mUtQJKFnGfaROTKOt1lZbDiiX1kCixRv7Nw2Id_sDs";

    @Test
    void parseUserFriendlyBounceableMainnet() {
        TonAddress addr = TonAddress.parse(USER_FRIENDLY);
        assertEquals(0, addr.workchain());
        assertTrue(addr.bounceable());
        assertFalse(addr.testnet());
        assertEquals(32, addr.hash().length);
    }

    @Test
    void roundtripThroughString() {
        TonAddress addr = TonAddress.parse(USER_FRIENDLY);
        assertEquals(USER_FRIENDLY, addr.toString());
    }

    @Test
    void rawFormParsesToSameHash() {
        TonAddress parsed = TonAddress.parse(USER_FRIENDLY);
        TonAddress again = TonAddress.parse(parsed.raw());
        assertEquals(parsed.workchain(), again.workchain());
        assertEquals(parsed, again);
    }

    @Test
    void crcMismatchRejected() {
        String tampered = USER_FRIENDLY.substring(0, USER_FRIENDLY.length() - 2) + "AA";
        assertThrows(IllegalArgumentException.class, () -> TonAddress.parse(tampered));
    }
}
