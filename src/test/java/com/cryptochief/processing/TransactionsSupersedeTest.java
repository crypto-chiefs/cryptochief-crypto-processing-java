package com.cryptochief.processing;

import com.cryptochief.processing.exceptions.ApiException;
import com.cryptochief.processing.exceptions.ErrorCode;
import com.cryptochief.processing.models.SignTransactionRequest;
import com.cryptochief.processing.models.SignTransactionResponse;
import com.cryptochief.processing.models.TransactionInfo;
import com.cryptochief.processing.models.TxStatus;
import com.cryptochief.processing.poll.Polling;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * EVM signature supersede: the {@code cancelled} status, {@code superseded_uuids} on the sign
 * answer, {@code error_reason} on the transaction, and the new error codes.
 */
class TransactionsSupersedeTest {

    private static final String OLD = "0c1d9f3e-5a7b-4c2e-9f1a-3b6d8e2f4a10";
    private static final String NEW = "b4ee6a7a-f7c2-474d-b002-e83ebe3e78db";

    private static final SignTransactionRequest SIGN = new SignTransactionRequest(
            Chain.ETH_MAINNET, "0xfrom", "native", "0xto", "1", null, null, null);

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
    void cancelledIsFinal() {
        assertEquals("cancelled", TxStatus.CANCELLED);
        assertTrue(TxStatus.TERMINAL.contains("cancelled"));
        for (String live : List.of(TxStatus.SIGNED, TxStatus.BROADCASTING, TxStatus.BROADCASTED)) {
            assertFalse(TxStatus.TERMINAL.contains(live), live);
        }
    }

    @Test
    void waitingReturnsASupersededSignatureAtOnce() {
        server.enqueue(new MockResponse().setBody(
                "{\"uuid\":\"" + OLD + "\",\"status\":\"cancelled\",\"error_reason\":\"SUPERSEDED_BY:" + NEW + "\"}"));
        server.enqueue(new MockResponse().setBody(
                "{\"uuid\":\"" + OLD + "\",\"status\":\"cancelled\",\"error_reason\":\"SUPERSEDED_BY:" + NEW + "\"}"));

        TransactionInfo t = Polling.waitForTransaction(client, OLD,
                new PollOptions(Duration.ofMillis(10), Duration.ofMillis(300)));

        assertEquals(TxStatus.CANCELLED, t.status());
        assertEquals("SUPERSEDED_BY:" + NEW, t.errorReason());
        assertTrue(t.isTerminal());
        assertEquals(1, server.getRequestCount());
    }

    @Test
    void signReadsTheReplacedSignatures() {
        server.enqueue(new MockResponse().setBody("""
                {"uuid": "%s", "status": "signed", "network": "ETH_MAINNET", "chain_family": "EVM",
                 "signed_tx_hex": "0x02", "tx_hash": "0xabc", "expires_at": "2026-06-01T12:10:00Z",
                 "superseded_uuids": ["%s"]}
                """.formatted(NEW, OLD)));

        SignTransactionResponse r = client.transactions().sign(SIGN);

        assertEquals(List.of(OLD), r.supersededUuids());
    }

    @Test
    void signWithoutReplacedSignaturesReadsEmpty() {
        server.enqueue(new MockResponse().setBody("{\"uuid\":\"" + NEW + "\",\"status\":\"signed\"}"));

        SignTransactionResponse r = client.transactions().sign(SIGN);

        assertEquals(List.of(), r.supersededUuids());
    }

    @Test
    void infoReadsTheNonceGapReason() {
        String reason = "NONCE_GAP: missing_nonce=7 blocking_uuid=" + OLD;
        server.enqueue(new MockResponse().setBody(
                "{\"uuid\":\"" + NEW + "\",\"status\":\"signed\",\"error_reason\":\"" + reason + "\"}"));

        TransactionInfo t = client.transactions().info(NEW);

        assertEquals(reason, t.errorReason());
    }

    @ParameterizedTest
    @ValueSource(strings = {ErrorCode.NONCE_GAP, ErrorCode.NONCE_ALREADY_USED})
    void executeSurfacesTheNonceCodes(String code) {
        server.enqueue(new MockResponse().setResponseCode(400)
                .setBody("{\"error\":\"SERVICE_ERROR\",\"msg\":\"" + code + "\",\"ok\":false}"));

        ApiException e = assertThrows(ApiException.class, () -> client.transactions().execute(NEW));

        assertEquals(code, e.code());
        assertEquals(400, e.status());
    }

    @Test
    void signIsRefusedWhileAnExecuteIsUnresolved() {
        server.enqueue(new MockResponse().setResponseCode(400).setBody(
                "{\"error\":\"SERVICE_ERROR\",\"msg\":\"PREVIOUS_EXECUTE_UNRESOLVED: uuid=" + OLD + "\",\"ok\":false}"));

        ApiException e = assertThrows(ApiException.class, () -> client.transactions().sign(SIGN));

        assertTrue(e.code().startsWith(ErrorCode.PREVIOUS_EXECUTE_UNRESOLVED), e.code());
        assertTrue(e.code().endsWith(OLD), e.code());
    }

    @Test
    void theReleasedConstructorsStay() {
        SignTransactionResponse r = new SignTransactionResponse(NEW, "signed", "0x02", "0xabc",
                "2026-06-01T12:10:00Z", "EVM", Chain.ETH_MAINNET);
        assertEquals(List.of(), r.supersededUuids());

        TransactionInfo t = new TransactionInfo("u", "confirmed", Chain.ETH_MAINNET, "EVM", "0xa",
                "0xb", "native", "1", "ETH", null, "0x01", 12, 12, null, null, 7L, null, null, null,
                null, null);
        assertEquals(12, t.confirmations());
        assertEquals(null, t.errorReason());
    }
}
