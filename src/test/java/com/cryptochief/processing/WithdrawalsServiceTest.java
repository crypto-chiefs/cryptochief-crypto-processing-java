package com.cryptochief.processing;

import com.cryptochief.processing.models.HistoryQuery;
import com.cryptochief.processing.models.Withdrawal;
import com.cryptochief.processing.models.WithdrawalHistoryResponse;
import com.cryptochief.processing.models.WithdrawalStatus;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Confirmation counts on a manual withdrawal: {@code confirmations} absent until the
 * transaction has been seen in a block, then counting up through {@code confirm_check}, and
 * kept at its final value on {@code completed}; {@code required_confirmations} on every
 * withdrawal. The bodies are the shape the platform sends.
 */
class WithdrawalsServiceTest {

    private static final String SETTLING = """
            {
              "uuid": "b0d1f7f9-1eaa-4c2f-8f9b-2b0d1b0b9f11", "status": "confirm_check",
              "from_address": "0xF9e8d7c6b5a43210fedcba9876543210fedcba98",
              "to_address": "0xA1b2C3d4E5f6789012345678901234567890abcd",
              "amount": "100.500000", "network": "ETH_MAINNET", "coin": "USDT",
              "need_refuel": true, "refuel_tx_hash": "0xrefuel", "refuel_status": "done",
              "tx_hash": "0xmain", "confirmations": 4, "required_confirmations": 12,
              "estimated_fee_fiat": "1.20", "fee_mode": "service",
              "created_at": "2026-09-14T12:00:00Z"
            }
            """;

    private static final String QUEUED = """
            {
              "uuid": "c1e2a8a0-2fbb-4d3a-9a0c-3c1e2c1c0a22", "status": "queue",
              "from_address": "bc1qsource", "to_address": "bc1qdest",
              "amount": "0.01", "network": "BTC_MAINNET", "coin": "BTC",
              "need_refuel": false, "required_confirmations": 2,
              "estimated_fee_fiat": "0.80", "fee_mode": "client",
              "created_at": "2026-09-14T12:05:00Z"
            }
            """;

    private MockWebServer server;
    private CryptoChiefClient client;

    @BeforeEach
    void setUp() throws Exception {
        server = new MockWebServer();
        server.start();
        client = new CryptoChiefClient(Options.builder()
                .merchantId("mer_test")
                .apiKey("secret-key")
                .baseUrl(server.url("/").toString().replaceAll("/$", ""))
                .build());
    }

    @AfterEach
    void tearDown() throws Exception {
        client.close();
        server.shutdown();
    }

    @Test
    void infoReadsConfirmationsAndTheDepthWhileSettling() throws Exception {
        server.enqueue(new MockResponse().setBody(SETTLING));

        Withdrawal w = client.withdrawals().info("b0d1f7f9-1eaa-4c2f-8f9b-2b0d1b0b9f11");

        RecordedRequest req = server.takeRequest();
        assertEquals("/v1/withdrawal/info", req.getPath());
        assertTrue(req.getBody().readUtf8().contains("\"uuid\":\"b0d1f7f9-1eaa-4c2f-8f9b-2b0d1b0b9f11\""));

        assertEquals(WithdrawalStatus.CONFIRM_CHECK, w.status());
        assertEquals(4, w.confirmations());
        assertEquals(12, w.requiredConfirmations());
        // In a block but short of the depth: in flight, not settled.
        assertFalse(w.isTerminal());
        assertFalse(w.succeeded());
        assertNull(w.completedAt());

        assertEquals(Chain.ETH_MAINNET, w.network());
        assertEquals("0xmain", w.txHash());
        assertTrue(w.needRefuel());
        assertEquals("0xrefuel", w.refuelTxHash());
        assertEquals("done", w.refuelStatus());
        assertEquals("1.20", w.estimatedFeeFiat());
        assertNull(w.actualFeeFiat());
        assertEquals("service", w.feeMode());
    }

    @Test
    void confirmationsIsNullUntilTheTransactionIsSeenInABlock() {
        server.enqueue(new MockResponse().setBody(QUEUED));

        Withdrawal w = client.withdrawals().info("c1e2a8a0-2fbb-4d3a-9a0c-3c1e2c1c0a22");

        assertEquals(WithdrawalStatus.QUEUE, w.status());
        assertNull(w.confirmations());
        // The depth is sent before there is anything to count.
        assertEquals(2, w.requiredConfirmations());
        assertFalse(w.needRefuel());
        assertNull(w.txHash());
    }

    @Test
    void confirmCheckBeforeTheFirstBlockHasNoCountYet() {
        // Outside the UTXO family a withdrawal enters confirm_check as soon as it is sent, so
        // the status alone does not say the transaction is in a block.
        server.enqueue(new MockResponse().setBody(SETTLING.replace(" \"confirmations\": 4,", "")));

        Withdrawal w = client.withdrawals().info("b0d1f7f9-1eaa-4c2f-8f9b-2b0d1b0b9f11");

        assertEquals(WithdrawalStatus.CONFIRM_CHECK, w.status());
        assertNull(w.confirmations());
        assertEquals(12, w.requiredConfirmations());
    }

    @Test
    void aZeroCountArrivesAsZeroNotNull() {
        server.enqueue(new MockResponse().setBody(SETTLING.replace("\"confirmations\": 4", "\"confirmations\": 0")));

        Withdrawal w = client.withdrawals().info("b0d1f7f9-1eaa-4c2f-8f9b-2b0d1b0b9f11");

        assertEquals(0, w.confirmations());
    }

    @Test
    void aCompletedWithdrawalReadsAtLeastItsDepth() {
        server.enqueue(new MockResponse().setBody(SETTLING
                .replace("\"status\": \"confirm_check\"", "\"status\": \"completed\"")
                .replace("\"confirmations\": 4", "\"confirmations\": 12")
                .replace("\"fee_mode\": \"service\",",
                        "\"actual_fee_fiat\": \"1.18\", \"fee_mode\": \"service\", \"completed_at\": \"2026-09-14T12:04:30Z\",")));

        Withdrawal w = client.withdrawals().info("b0d1f7f9-1eaa-4c2f-8f9b-2b0d1b0b9f11");

        assertTrue(w.succeeded());
        assertTrue(w.isTerminal());
        assertTrue(w.confirmations() >= w.requiredConfirmations());
        assertEquals("2026-09-14T12:04:30Z", w.completedAt());
        assertEquals("1.18", w.actualFeeFiat());
    }

    @Test
    void aFailedWithdrawalCarriesItsReason() {
        server.enqueue(new MockResponse().setBody(SETTLING
                .replace("\"status\": \"confirm_check\"", "\"status\": \"failed\"")
                .replace(" \"confirmations\": 4,", "")
                .replace("\"fee_mode\": \"service\",", "\"error_reason\": \"TX_CONFIRM_TIMEOUT\", \"fee_mode\": \"service\",")));

        Withdrawal w = client.withdrawals().info("b0d1f7f9-1eaa-4c2f-8f9b-2b0d1b0b9f11");

        assertTrue(w.isTerminal());
        assertFalse(w.succeeded());
        assertEquals("TX_CONFIRM_TIMEOUT", w.errorReason());
        assertNull(w.confirmations());
        assertNull(w.completedAt());
    }

    @Test
    @SuppressWarnings("deprecation")
    void theLegacyCancelledValueStillDecodesAsTerminal() {
        server.enqueue(new MockResponse().setBody(QUEUED
                .replace("\"status\": \"queue\"", "\"status\": \"cancelled\"")));

        Withdrawal w = client.withdrawals().info("c1e2a8a0-2fbb-4d3a-9a0c-3c1e2c1c0a22");

        assertEquals(WithdrawalStatus.CANCELLED, w.status());
        assertTrue(w.isTerminal());
        assertFalse(w.succeeded());
    }

    @Test
    void requiredConfirmationsReadsAsNullAgainstAPlatformThatDoesNotSendIt() {
        String body = SETTLING.replace(" \"required_confirmations\": 12,", "");
        assertFalse(body.contains("required_confirmations"));
        server.enqueue(new MockResponse().setBody(body));

        Withdrawal w = client.withdrawals().info("b0d1f7f9-1eaa-4c2f-8f9b-2b0d1b0b9f11");

        assertNull(w.requiredConfirmations());
        assertEquals(4, w.confirmations());
    }

    @Test
    void historyItemsCarryTheirOwnCounts() throws Exception {
        server.enqueue(new MockResponse().setBody("{\"items\": [" + SETTLING + "," + QUEUED + "],"
                + "\"meta\": {\"page\": 1, \"page_size\": 20, \"total\": 2, \"total_pages\": 1}}"));

        WithdrawalHistoryResponse page = client.withdrawals().history(
                new HistoryQuery(1, 20, null, null, null, null, null));

        assertEquals("/v1/withdrawal/history", server.takeRequest().getPath());
        assertEquals(2, page.items().size());
        assertEquals(4, page.items().get(0).confirmations());
        assertEquals(12, page.items().get(0).requiredConfirmations());
        assertNull(page.items().get(1).confirmations());
        assertEquals(2, page.items().get(1).requiredConfirmations());
        assertEquals(Chain.BTC_MAINNET, page.items().get(1).network());
        assertEquals(2, page.meta().total());
    }

    @Test
    @SuppressWarnings("deprecation")
    void statusConstantsAreTheNamesThePlatformSends() {
        assertEquals(Set.of("completed", "failed", "cancelled"), WithdrawalStatus.TERMINAL);
        assertEquals("cancelled", WithdrawalStatus.CANCELLED);
        assertEquals("in_mempool", WithdrawalStatus.IN_MEMPOOL);
        assertEquals("confirm_check", WithdrawalStatus.CONFIRM_CHECK);
        assertEquals("refuel_confirmed", WithdrawalStatus.REFUEL_CONFIRMED);
        // paid and system_fail are payout statuses; a withdrawal with either is not terminal.
        assertFalse(WithdrawalStatus.TERMINAL.contains("paid"));
        assertFalse(WithdrawalStatus.TERMINAL.contains("system_fail"));
    }
}
