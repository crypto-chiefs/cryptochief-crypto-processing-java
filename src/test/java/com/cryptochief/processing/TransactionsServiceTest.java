package com.cryptochief.processing;

import com.cryptochief.processing.models.TransactionHistoryResponse;
import com.cryptochief.processing.models.TransactionInfo;
import com.cryptochief.processing.models.TxStatus;
import com.cryptochief.processing.poll.Polling;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Confirmation counts on a sign/execute transaction. Unlike a payout's, both fields are
 * always on the wire: {@code confirmations} is 0 while the transaction is not in a block,
 * grows while it is {@code broadcasted} and in one, and holds the count it was confirmed at;
 * {@code required_confirmations} is the network's threshold from signing onwards.
 */
class TransactionsServiceTest {

    private static String tx(String uuid, String status, int confirmations, int required) {
        return """
                {"uuid": "%s", "status": "%s", "network": "ETH_MAINNET", "chain_family": "EVM",
                 "type": "transfer", "from_address": "0xfrom", "to_address": "0xto", "value": "1000",
                 "tx_hash": "0xhash", "confirmations": %d, "required_confirmations": %d,
                 "expires_at": "2026-09-14T11:00:00Z", "created_at": "2026-09-14T10:00:00Z"}
                """.formatted(uuid, status, confirmations, required);
    }

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
    void infoReadsTheCountAndTheThresholdApplied() throws Exception {
        server.enqueue(new MockResponse().setBody(tx("t-1", "confirmed", 12, 12)));

        TransactionInfo t = client.transactions().info("t-1");

        assertEquals("/v1/transaction/info", server.takeRequest().getPath());
        assertEquals(TxStatus.CONFIRMED, t.status());
        assertEquals(12, t.confirmations());
        assertEquals(12, t.requiredConfirmations());
    }

    @Test
    void executeAnswersWithTheThresholdBeforeTheTransactionIsInABlock() throws Exception {
        server.enqueue(new MockResponse().setBody(tx("t-2", "broadcasted", 0, 12)));

        TransactionInfo t = client.transactions().execute("t-2");

        assertEquals("/v1/transaction/execute", server.takeRequest().getPath());
        assertEquals(TxStatus.BROADCASTED, t.status());
        assertEquals(0, t.confirmations());
        assertEquals(12, t.requiredConfirmations());
    }

    @Test
    void aBroadcastedTransactionInABlockReadsItsGrowingCountAndIsNotSettled() throws Exception {
        server.enqueue(new MockResponse().setBody(tx("t-5", "broadcasted", 5, 12)));

        TransactionInfo t = client.transactions().info("t-5");

        assertEquals("/v1/transaction/info", server.takeRequest().getPath());
        assertEquals(TxStatus.BROADCASTED, t.status());
        assertEquals(5, t.confirmations());
        assertEquals(12, t.requiredConfirmations());
        assertTrue(t.confirmations() < t.requiredConfirmations());
        assertFalse(t.isTerminal());
        assertFalse(t.succeeded());
    }

    @Test
    void theCountOfABroadcastedTransactionCanGoDownAfterAReorganisation() throws Exception {
        server.enqueue(new MockResponse().setBody(tx("t-6", "broadcasted", 3, 12)));
        server.enqueue(new MockResponse().setBody(tx("t-6", "broadcasted", 0, 12)));

        TransactionInfo before = client.transactions().info("t-6");
        TransactionInfo after = client.transactions().info("t-6");

        assertEquals(3, before.confirmations());
        assertEquals(0, after.confirmations());
        assertEquals(TxStatus.BROADCASTED, after.status());
        assertFalse(after.isTerminal());
    }

    @Test
    void waitForTransactionKeepsPollingThroughACountAboveZeroUntilTheStatusIsFinal() {
        server.enqueue(new MockResponse().setBody(tx("t-7", "broadcasted", 0, 12)));
        server.enqueue(new MockResponse().setBody(tx("t-7", "broadcasted", 4, 12)));
        server.enqueue(new MockResponse().setBody(tx("t-7", "broadcasted", 9, 12)));
        server.enqueue(new MockResponse().setBody(tx("t-7", "confirmed", 12, 12)));

        TransactionInfo t = Polling.waitForTransaction(client, "t-7",
                new PollOptions(Duration.ofMillis(1), Duration.ofSeconds(10)));

        assertEquals(4, server.getRequestCount());
        assertEquals(TxStatus.CONFIRMED, t.status());
        assertTrue(t.succeeded());
        assertEquals(12, t.confirmations());
        assertEquals(12, t.requiredConfirmations());
    }

    @Test
    void historyItemsCarryBothFieldsWhateverTheirStatus() throws Exception {
        server.enqueue(new MockResponse().setBody("{\"items\": ["
                + tx("t-3", "confirmed", 19, 19) + ","
                + tx("t-4", "failed", 0, 1)
                + "], \"meta\": {\"page\": 1, \"page_size\": 20, \"total\": 2, \"total_pages\": 1}}"));

        TransactionHistoryResponse page = client.transactions().history();

        assertEquals("/v1/transaction/history", server.takeRequest().getPath());
        assertEquals(19, page.items().get(0).confirmations());
        assertEquals(19, page.items().get(0).requiredConfirmations());
        assertEquals(TxStatus.FAILED, page.items().get(1).status());
        assertEquals(0, page.items().get(1).confirmations());
        assertEquals(1, page.items().get(1).requiredConfirmations());
    }
}
