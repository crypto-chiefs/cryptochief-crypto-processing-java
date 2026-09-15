package com.cryptochief.processing;

import com.cryptochief.processing.models.ExecutePayoutRequest;
import com.cryptochief.processing.models.PayoutHistoryResponse;
import com.cryptochief.processing.models.PayoutInfo;
import com.cryptochief.processing.models.PayoutServiceOperation;
import com.cryptochief.processing.models.PayoutSource;
import com.cryptochief.processing.poll.Polling;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Confirmation counts on a payout: the lowest-among-sources value at the top, a count per
 * source and per service operation, and all three absent until there is a transaction to
 * count; plus the network's finality depth, which a payout must reach on every source before
 * it is paid, and which is optional on the wire. The bodies are the shape the platform sends,
 * extra keys included - a source item carries far more than the SDK models, and it has to
 * decode anyway.
 */
class PayoutsServiceTest {

    private static final String SETTLING_PAYOUT = """
            {
              "uuid": "11111111-1111-4111-8111-111111111111", "order_id": "order-1", "user_id": "user-1",
              "status": "confirm_check", "amount_requested": "1.5", "amount_to_receive": "1.5", "to_address": "0xdest",
              "fee_info": {"fee_mode": "mix", "estimated_fiat": "0.40", "limit_fiat": "5", "limit_currency": "USD"},
              "sources": [
                {"address": "0xaaa", "network": "ETH_MAINNET", "coin": "ETH", "amount_crypto": "1.0",
                 "need_refuel": false, "refuel_amount": "0", "estimated_fee": "0.0001", "estimated_fee_fiat": "0.30",
                 "txid": "0x01", "confirmations": 15},
                {"address": "0xbbb", "network": "ETH_MAINNET", "coin": "ETH", "amount_crypto": "0.5",
                 "need_refuel": true, "refuel_amount": "0.001", "estimated_fee": "0.0001", "estimated_fee_fiat": "0.10",
                 "txid": "0x02", "confirmations": 4}
              ],
              "service_operations": [
                {"type": "gas_refuel", "context": "payout_prepare", "status": "done", "network": "ETH_MAINNET",
                 "coin": "ETH", "amount_native": "0.001", "from_address": "0xsvc", "to_address": "0xbbb",
                 "estimated_fee": "0.00002", "estimated_fee_fiat": "0.05", "confirmations": 20}
              ],
              "confirmations": 4, "required_confirmations": 12,
              "created_at": "2026-09-14T10:00:00Z", "completed_at": null
            }
            """;

    private static final String QUEUED_PAYOUT = """
            {
              "uuid": "22222222-2222-4222-8222-222222222222", "order_id": "order-2", "user_id": "user-1",
              "status": "queue", "amount_requested": "1", "amount_to_receive": "1", "to_address": "0xdest",
              "fee_info": {"fee_mode": "mix", "estimated_fiat": "0.30", "limit_currency": "USD"},
              "sources": [
                {"address": "0xaaa", "network": "ETH_MAINNET", "coin": "ETH", "amount_crypto": "1",
                 "need_refuel": false, "refuel_amount": "0", "estimated_fee": "0.0001", "estimated_fee_fiat": "0.30"}
              ],
              "created_at": "2026-09-14T10:05:00Z", "completed_at": null
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
    void infoReadsConfirmationsOnSourcesServiceOperationsAndTopLevel() throws Exception {
        server.enqueue(new MockResponse().setBody(SETTLING_PAYOUT));

        PayoutInfo p = client.payouts().info("11111111-1111-4111-8111-111111111111");

        RecordedRequest req = server.takeRequest();
        assertEquals("/v1/payout/info", req.getPath());

        assertEquals("confirm_check", p.status());
        assertEquals(4, p.confirmations());
        assertEquals(12, p.requiredConfirmations());
        assertEquals(2, p.sources().size());
        PayoutSource first = p.sources().get(0);
        assertEquals("0xaaa", first.address());
        assertEquals(Chain.ETH_MAINNET, first.network());
        assertEquals("ETH", first.coin());
        assertEquals("1.0", first.amountCrypto());
        assertNull(first.amount());
        assertEquals(Boolean.FALSE, first.needRefuel());
        assertEquals("0.0001", first.estimatedFee());
        assertEquals("0.30", first.estimatedFeeFiat());
        assertEquals("0x01", first.txid());
        assertEquals(15, first.confirmations());
        PayoutSource second = p.sources().get(1);
        assertEquals("0.5", second.amountCrypto());
        assertEquals(Boolean.TRUE, second.needRefuel());
        assertEquals("0.001", second.refuelAmount());
        assertEquals("0x02", second.txid());
        assertEquals(4, second.confirmations());

        assertEquals(1, p.serviceOperations().size());
        PayoutServiceOperation refuel = p.serviceOperations().get(0);
        assertEquals("gas_refuel", refuel.type());
        assertEquals("done", refuel.status());
        assertEquals(Chain.ETH_MAINNET, refuel.network());
        assertEquals("0xbbb", refuel.toAddress());
        assertEquals(20, refuel.confirmations());
        assertNull(refuel.txid());
    }

    @Test
    void confirmationsAreNullWhileNothingHasBeenSent() {
        server.enqueue(new MockResponse().setBody(QUEUED_PAYOUT));

        PayoutInfo p = client.payouts().info("22222222-2222-4222-8222-222222222222");

        assertEquals("queue", p.status());
        assertNull(p.confirmations());
        assertEquals(1, p.sources().size());
        assertEquals("1", p.sources().get(0).amountCrypto());
        assertNull(p.sources().get(0).txid());
        assertNull(p.sources().get(0).confirmations());
        assertNull(p.serviceOperations());
    }

    @Test
    void aZeroCountArrivesAsZeroNotNull() {
        // 0 is a source whose transaction is not in a block yet. It is a count, so it must not
        // collapse into "not observed yet".
        server.enqueue(new MockResponse().setBody(SETTLING_PAYOUT
                .replace("\"confirmations\": 15", "\"confirmations\": 0")
                .replace("\"confirmations\": 4}", "\"confirmations\": 0}")
                .replace("\"confirmations\": 4,", "\"confirmations\": 0,")));

        PayoutInfo p = client.payouts().info("11111111-1111-4111-8111-111111111111");

        assertEquals(0, p.confirmations());
        assertEquals(0, p.sources().get(0).confirmations());
        assertEquals(0, p.sources().get(1).confirmations());
    }

    @Test
    void aSourceWithTxidHasNoCountUntilOnChain() {
        // The txid is written before the first count: the top level reads 0, the source null.
        server.enqueue(new MockResponse().setBody(SETTLING_PAYOUT
                .replace(", \"confirmations\": 15}", "}")
                .replace(", \"confirmations\": 4}", "}")
                .replace("\"confirmations\": 4,", "\"confirmations\": 0,")));

        PayoutInfo p = client.payouts().info("11111111-1111-4111-8111-111111111111");

        assertEquals(0, p.confirmations());
        for (PayoutSource s : p.sources()) {
            assertNotNull(s.txid());
            assertNull(s.confirmations());
        }
    }

    @Test
    void aPaidPayoutReadsAtLeastItsDepthWhenTheScannerReportedFinal() {
        // A source the scanner reports as "final, no block count" is published with its count
        // equal to the depth, as on sweeps - so a paid payout reads at least the depth.
        server.enqueue(new MockResponse().setBody(SETTLING_PAYOUT
                .replace("\"status\": \"confirm_check\"", "\"status\": \"paid\"")
                .replace("\"confirmations\": 4}", "\"confirmations\": 12}")
                .replace("\"confirmations\": 4,", "\"confirmations\": 12,")));

        PayoutInfo p = client.payouts().info("11111111-1111-4111-8111-111111111111");

        assertTrue(p.succeeded());
        assertEquals(12, p.confirmations());
        assertEquals(12, p.requiredConfirmations());
        assertTrue(p.confirmations() >= p.requiredConfirmations());
        for (PayoutSource s : p.sources()) {
            assertTrue(s.confirmations() >= p.requiredConfirmations());
        }
    }

    @Test
    void requiredConfirmationsIsOptionalAndItsAbsenceBreaksNothing() {
        // Sent on practically every payout, but not guaranteed: an absent key reads as null
        // and every other count still decodes.
        String body = SETTLING_PAYOUT.replace(" \"required_confirmations\": 12,", "");
        assertFalse(body.contains("required_confirmations"));
        server.enqueue(new MockResponse().setBody(body));

        PayoutInfo p = client.payouts().info("11111111-1111-4111-8111-111111111111");

        assertNull(p.requiredConfirmations());
        assertEquals(4, p.confirmations());
        assertEquals(15, p.sources().get(0).confirmations());
        assertEquals(20, p.serviceOperations().get(0).confirmations());
    }

    @Test
    void historyItemsCarryTheirOwnConfirmations() throws Exception {
        server.enqueue(new MockResponse().setBody("{\"items\": [" + SETTLING_PAYOUT + "," + QUEUED_PAYOUT + "],"
                + "\"meta\": {\"page\": 1, \"page_size\": 20, \"total\": 2, \"total_pages\": 1}}"));

        PayoutHistoryResponse page = client.payouts().history();

        assertEquals("/v1/payout/history", server.takeRequest().getPath());
        assertEquals(2, page.items().size());
        assertEquals(4, page.items().get(0).confirmations());
        assertEquals(12, page.items().get(0).requiredConfirmations());
        assertEquals(15, page.items().get(0).sources().get(0).confirmations());
        assertNull(page.items().get(1).confirmations());
        assertNull(page.items().get(1).requiredConfirmations());
        assertNull(page.items().get(1).sources().get(0).confirmations());
    }

    @Test
    void repeatedExecuteReturnsTheStoredPayoutWithItsConfirmations() throws Exception {
        // A second execute with an order_id that already exists answers with the existing
        // payout, counts and all.
        server.enqueue(new MockResponse().setBody(SETTLING_PAYOUT));

        PayoutInfo p = client.payouts().execute(new ExecutePayoutRequest(
                "order-1", "user-1", Chain.ETH_MAINNET, "ETH", "1.5", "0xdest",
                null, null, false, false, null, null, null));

        assertEquals("/v1/payout/execute", server.takeRequest().getPath());
        assertEquals("11111111-1111-4111-8111-111111111111", p.uuid());
        assertEquals(4, p.confirmations());
        assertEquals(12, p.requiredConfirmations());
        assertEquals(20, p.serviceOperations().get(0).confirmations());
    }

    @Test
    void waitForPayoutKeepsPollingThroughConfirmCheckUntilPaid() {
        server.enqueue(new MockResponse().setBody(SETTLING_PAYOUT));
        server.enqueue(new MockResponse().setBody(SETTLING_PAYOUT
                .replace("\"confirmations\": 4}", "\"confirmations\": 11}")
                .replace("\"confirmations\": 4,", "\"confirmations\": 11,")));
        server.enqueue(new MockResponse().setBody(SETTLING_PAYOUT
                .replace("\"status\": \"confirm_check\"", "\"status\": \"paid\"")
                .replace("\"confirmations\": 4}", "\"confirmations\": 12}")
                .replace("\"confirmations\": 4,", "\"confirmations\": 12,")));

        PayoutInfo p = Polling.waitForPayout(client, "11111111-1111-4111-8111-111111111111",
                new PollOptions(Duration.ofMillis(1), Duration.ofSeconds(10)));

        assertEquals(3, server.getRequestCount());
        assertTrue(p.succeeded());
        assertEquals(12, p.confirmations());
    }

    @Test
    void waitForPayoutDefaultTimeoutIsNinetyMinutes() {
        assertEquals(Duration.ofMinutes(90), PollOptions.payoutDefaults().timeout());
        assertEquals(Duration.ofMinutes(10), PollOptions.defaults().timeout());
    }
}
