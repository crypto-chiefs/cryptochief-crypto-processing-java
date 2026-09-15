package com.cryptochief.processing;

import com.cryptochief.processing.models.PayoutInfo;
import com.cryptochief.processing.models.PayoutSource;
import com.cryptochief.processing.models.Sweep;
import com.cryptochief.processing.models.TransactionInfo;
import com.cryptochief.processing.models.Withdrawal;
import com.cryptochief.processing.webhook.PayoutWebhookEvent;
import com.cryptochief.processing.webhook.SweepWebhookEvent;
import com.cryptochief.processing.webhook.TransactionWebhookEvent;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

/** Code that builds these records with the 0.8.0 constructors keeps compiling. */
class ReleasedConstructorsTest {

    @Test
    void modelsKeepTheirReleasedConstructors() {
        PayoutSource source = new PayoutSource("0xa", "1", "ETH");
        assertEquals("0xa", source.address());
        assertNull(source.amountCrypto());
        assertNull(source.confirmations());

        PayoutInfo payout = new PayoutInfo("u", "o", "paid", Chain.ETH_MAINNET, "ETH", "1", "0xb",
                "0x01", List.of(source), null, null, null, null);
        assertEquals("paid", payout.status());
        assertEquals(List.of(source), payout.sources());
        assertNull(payout.serviceOperations());
        assertNull(payout.confirmations());
        assertNull(payout.requiredConfirmations());

        Sweep sweep = new Sweep("t", "0x01", null, "completed", "0xa", Chain.ETH_MAINNET, null,
                "ETH", "native", "1", "force", 12, "2026-09-14T10:00:00Z", null, null, null, null,
                null, null, null, null, null, null, null, null, null, null, null);
        assertEquals(12, sweep.sweepConfirmations());
        assertEquals("2026-09-14T10:00:00Z", sweep.completedAt());
        assertNull(sweep.requiredConfirmations());

        TransactionInfo tx = new TransactionInfo("u", "confirmed", Chain.ETH_MAINNET, "EVM", "0xa",
                "0xb", "transfer", "1", "ETH", null, "0x01", null, null, 7L, null, null, null, null,
                null);
        assertEquals("0x01", tx.txHash());
        assertEquals(7L, tx.nonce());
        assertEquals(0, tx.confirmations());
        assertEquals(0, tx.requiredConfirmations());

        Withdrawal w = new Withdrawal("u", "completed", Chain.ETH_MAINNET, "ETH", null, "1", null,
                "0xa", "0xb", "0x01", "2026-09-14T10:00:00Z", null, null, null);
        assertEquals("0x01", w.txHash());
        assertEquals("2026-09-14T10:00:00Z", w.createdAt());
        assertFalse(w.needRefuel());
        assertNull(w.confirmations());
        assertNull(w.completedAt());
    }

    @Test
    void webhookEventsKeepTheirReleasedConstructors() {
        PayoutWebhookEvent payout = new PayoutWebhookEvent("payout.paid", "u", "o", "user", "paid",
                "1", "1", "0xb", null, null, null, "2026-09-14T10:00:00Z", null, "INTERNAL_ERROR");
        assertEquals("2026-09-14T10:00:00Z", payout.createdAt());
        assertEquals("INTERNAL_ERROR", payout.errorReason());
        assertNull(payout.confirmations());
        assertNull(payout.requiredConfirmations());

        SweepWebhookEvent sweep = new SweepWebhookEvent(SweepWebhookEvent.EVENT_CONFIRMED, "t",
                "completed", "0xa", "0xb", Chain.ETH_MAINNET, "EVM", "ETH", null, "native", "1",
                "1", "0x01", null, 12, "2026-09-14T10:00:00Z", "force", "0.10");
        assertEquals(12, sweep.sweepConfirmations());
        assertEquals("2026-09-14T10:00:00Z", sweep.confirmedAt());
        assertNull(sweep.requiredConfirmations());

        TransactionWebhookEvent tx = new TransactionWebhookEvent("transaction.confirmed", "u",
                "confirmed", Chain.ETH_MAINNET, "EVM", "transfer", "0xa", "0xb", "1", null, "0x01",
                "2026-09-14T10:00:00Z", null, null);
        assertEquals("0x01", tx.txHash());
        assertEquals("2026-09-14T10:00:00Z", tx.createdAt());
        assertEquals(0, tx.confirmations());
    }
}
