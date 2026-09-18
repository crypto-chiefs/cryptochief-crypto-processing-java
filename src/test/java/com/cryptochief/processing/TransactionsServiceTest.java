package com.cryptochief.processing;

import com.cryptochief.processing.exceptions.ApiException;
import com.cryptochief.processing.exceptions.ErrorCode;
import com.cryptochief.processing.models.EstimateTransactionRequest;
import com.cryptochief.processing.models.EstimateTransactionResponse;
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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
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
    void estimateQuotesTheFeeAndTheBalanceANativeTransferNeeds() throws Exception {
        server.enqueue(new MockResponse().setBody("""
                {"network": "ETH_MAINNET", "chain_family": "EVM", "type": "native",
                 "from_address": "0xfrom", "to_address": "0xto",
                 "estimated_fee": "0.000021", "estimated_fee_fiat": "0.08",
                 "required": "0.010021", "required_fiat": "38.12"}
                """));

        EstimateTransactionResponse e = client.transactions().estimate(
                EstimateTransactionRequest.of(Chain.ETH_MAINNET, "0xfrom", "0xto", "10000000000000000"));

        var request = server.takeRequest();
        assertEquals("/v1/transaction/estimate", request.getPath());
        assertEquals("{\"from_address\":\"0xfrom\",\"network\":\"ETH_MAINNET\",\"to_address\":\"0xto\","
                + "\"type\":\"native\",\"value\":\"10000000000000000\"}", request.getBody().readUtf8());
        assertEquals(Chain.ETH_MAINNET, e.network());
        assertEquals("native", e.type());
        // fee + value: what the from-wallet must hold for a native transfer
        assertEquals("0.000021", e.estimatedFee());
        assertEquals("0.08", e.estimatedFeeFiat());
        assertEquals("0.010021", e.required());
        assertEquals("38.12", e.requiredFiat());
    }

    @Test
    void estimateQuotesTheFeeOfATokenTransferWithEmptyFiatWhenNoRateIsAvailable() throws Exception {
        server.enqueue(new MockResponse().setBody("""
                {"network": "TRON_MAINNET", "chain_family": "TRON", "type": "token",
                 "from_address": "TFrom", "to_address": "TTo",
                 "estimated_fee": "13.5", "estimated_fee_fiat": "",
                 "required": "13.5", "required_fiat": ""}
                """));

        EstimateTransactionResponse e = client.transactions().estimate(
                EstimateTransactionRequest.ofToken(Chain.TRON_MAINNET, "TFrom", "TTo", "5000000",
                        "TR7NHqjeKQxGTCi8q8ZY4pL8otSzgjLj6t"));

        var request = server.takeRequest();
        assertEquals("/v1/transaction/estimate", request.getPath());
        assertEquals("{\"contract\":\"TR7NHqjeKQxGTCi8q8ZY4pL8otSzgjLj6t\",\"from_address\":\"TFrom\","
                + "\"network\":\"TRON_MAINNET\",\"to_address\":\"TTo\",\"type\":\"token\",\"value\":\"5000000\"}",
                request.getBody().readUtf8());
        // the fee alone: the token itself, not the native coin, covers the value
        assertEquals("token", e.type());
        assertEquals("13.5", e.estimatedFee());
        assertEquals("13.5", e.required());
        assertEquals("", e.estimatedFeeFiat());
        assertEquals("", e.requiredFiat());
    }

    @Test
    void estimateBreaksTheFeeDownIntoEnergyBandwidthAndActivationOnTron() throws Exception {
        server.enqueue(new MockResponse().setBody("""
                {"network": "TRON_MAINNET", "chain_family": "TRON", "type": "token",
                 "from_address": "TFrom", "to_address": "TTo",
                 "estimated_fee": "14.2", "estimated_fee_fiat": "4.15",
                 "required": "14.2", "required_fiat": "4.15",
                 "fee_expected": "0.0", "fee_limit": "30.0",
                 "energy": 65000, "energy_fee": "13.0",
                 "bandwidth_fee": "0.3", "activation_fee": "0.9"}
                """));

        EstimateTransactionResponse e = client.transactions().estimate(
                EstimateTransactionRequest.ofToken(Chain.TRON_MAINNET, "TFrom", "TTo", "5000000",
                        "TR7NHqjeKQxGTCi8q8ZY4pL8otSzgjLj6t"));

        assertEquals("/v1/transaction/estimate", server.takeRequest().getPath());
        // energy_fee + bandwidth_fee + activation_fee = estimated_fee (gross)
        assertEquals("14.2", e.estimatedFee());
        assertEquals("13.0", e.energyFee());
        assertEquals("0.3", e.bandwidthFee());
        assertEquals("0.9", e.activationFee());
        // what the wallet's current energy pool is expected to cover, and the on-chain cap
        assertEquals("0.0", e.feeExpected());
        assertEquals("30.0", e.feeLimit());
        assertEquals(65000L, e.energy());
    }

    @Test
    void estimateLeavesTheTronBreakdownNullOnOtherNetworks() throws Exception {
        server.enqueue(new MockResponse().setBody("""
                {"network": "ETH_MAINNET", "chain_family": "EVM", "type": "native",
                 "from_address": "0xfrom", "to_address": "0xto",
                 "estimated_fee": "0.000021", "estimated_fee_fiat": "0.08",
                 "required": "0.010021", "required_fiat": "38.12"}
                """));

        EstimateTransactionResponse e = client.transactions().estimate(
                EstimateTransactionRequest.of(Chain.ETH_MAINNET, "0xfrom", "0xto", "10000000000000000"));

        assertEquals("/v1/transaction/estimate", server.takeRequest().getPath());
        assertNull(e.feeExpected());
        assertNull(e.feeLimit());
        assertNull(e.energy());
        assertNull(e.energyFee());
        assertNull(e.bandwidthFee());
        assertNull(e.activationFee());
    }

    @Test
    void estimateOfAContractCallPropagatesTheRefusal() {
        server.enqueue(new MockResponse().setResponseCode(400)
                .setBody("{\"ok\":false,\"error\":\"CONTRACT_ESTIMATE_UNSUPPORTED\","
                        + "\"msg\":\"contract calls cannot be estimated\"}"));

        ApiException ex = assertThrows(ApiException.class, () -> client.transactions().estimate(
                new EstimateTransactionRequest(Chain.ETH_MAINNET, "0xfrom", "contract",
                        null, null, null)));

        assertEquals(ErrorCode.CONTRACT_ESTIMATE_UNSUPPORTED, ex.code());
        assertEquals(400, ex.status());
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
