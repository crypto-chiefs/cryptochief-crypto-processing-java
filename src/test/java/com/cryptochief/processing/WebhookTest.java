package com.cryptochief.processing;

import com.cryptochief.processing.http.CanonicalJson;
import com.cryptochief.processing.http.RequestSigner;
import com.cryptochief.processing.webhook.PayoutWebhookEvent;
import com.cryptochief.processing.webhook.SweepWebhookEvent;
import com.cryptochief.processing.webhook.TransactionWebhookEvent;
import com.cryptochief.processing.webhook.WebhookSignatureException;
import com.cryptochief.processing.webhook.WebhookVerifier;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WebhookTest {

    private static final String API_KEY = "secret";

    @Test
    void acceptsCanonicalBody() throws Exception {
        String body = "{\"event\":\"payout.paid\",\"status\":\"paid\",\"uuid\":\"u\",\"order_id\":\"o\"}";
        byte[] canonical = CanonicalJson.encode(CanonicalJson.MAPPER.readTree(body));
        String sig = RequestSigner.sign(canonical, API_KEY);
        assertTrue(WebhookVerifier.verify(API_KEY, body.getBytes(StandardCharsets.UTF_8), sig));
    }

    @Test
    void acceptsReorderedBody() {
        Map<String, Object> ordered = new LinkedHashMap<>();
        ordered.put("a", 1);
        ordered.put("b", 2);
        byte[] canonical = CanonicalJson.encode(ordered);
        String sig = RequestSigner.sign(canonical, API_KEY);
        String reordered = "{\"b\":2,\"a\":1}";
        assertTrue(WebhookVerifier.verify(API_KEY, reordered.getBytes(StandardCharsets.UTF_8), sig));
    }

    @Test
    void rejectsMutatedBody() throws Exception {
        String body = "{\"event\":\"payout.paid\",\"uuid\":\"u\",\"order_id\":\"o\",\"status\":\"paid\"}";
        byte[] canonical = CanonicalJson.encode(CanonicalJson.MAPPER.readTree(body));
        String sig = RequestSigner.sign(canonical, API_KEY);
        String tampered = body.replace("\"paid\"", "\"failed\"");
        assertFalse(WebhookVerifier.verify(API_KEY, tampered.getBytes(StandardCharsets.UTF_8), sig));
    }

    @Test
    void parseReturnsTypedEvent() throws Exception {
        String body = "{\"event\":\"payout.paid\",\"uuid\":\"u-1\",\"order_id\":\"o-1\",\"status\":\"paid\"}";
        byte[] canonical = CanonicalJson.encode(CanonicalJson.MAPPER.readTree(body));
        String sig = RequestSigner.sign(canonical, API_KEY);
        PayoutWebhookEvent event = WebhookVerifier.parse(
                API_KEY, body.getBytes(StandardCharsets.UTF_8), sig, PayoutWebhookEvent.class);
        assertEquals("u-1", event.uuid());
        assertEquals("paid", event.status());
    }

    @Test
    void payoutWebhookCarriesConfirmationsAtTheTopAndOnEveryItem() throws Exception {
        String body = """
                {"event": "payout.paid", "uuid": "u-2", "order_id": "o-2", "user_id": "user-1", "status": "paid",
                 "amount_requested": "1.5", "amount_to_receive": "1.5", "to_address": "0xdest",
                 "fee_info": {"fee_mode": "mix", "limit_currency": "USD", "total_fee_paid_fiat": "0.41"},
                 "sources": [
                   {"address": "0xaaa", "network": "ETH_MAINNET", "coin": "ETH", "amount_crypto": "1.0",
                    "need_refuel": false, "refuel_amount": "0", "estimated_fee": "0.0001", "estimated_fee_fiat": "0.30",
                    "txid": "0x01", "confirmations": 15},
                   {"address": "0xbbb", "network": "ETH_MAINNET", "coin": "ETH", "amount_crypto": "0.5",
                    "need_refuel": true, "refuel_amount": "0.001", "estimated_fee": "0.0001", "estimated_fee_fiat": "0.10",
                    "txid": "0x02", "confirmations": 6}
                 ],
                 "service_operations": [
                   {"type": "gas_refuel", "context": "payout_prepare", "status": "done", "network": "ETH_MAINNET",
                    "coin": "ETH", "amount_native": "0.001", "from_address": "0xsvc", "to_address": "0xbbb",
                    "txid": "0x03", "confirmations": 20}
                 ],
                 "confirmations": 6, "required_confirmations": 6,
                 "created_at": "2026-09-14T10:00:00Z", "completed_at": "2026-09-14T10:04:00Z"}
                """;
        PayoutWebhookEvent event = WebhookVerifier.parse(
                API_KEY, body.getBytes(StandardCharsets.UTF_8), sign(body), PayoutWebhookEvent.class);

        assertEquals(6, event.confirmations());
        // payout.paid goes out only once every source reached the network's depth.
        assertEquals(6, event.requiredConfirmations());
        assertEquals(15, event.sources().get(0).get("confirmations").asInt());
        assertEquals(6, event.sources().get(1).get("confirmations").asInt());
        assertEquals(20, event.serviceOperations().get(0).get("confirmations").asInt());
    }

    @Test
    void systemFailPayoutWebhookWithoutTransactionsHasNoConfirmations() throws Exception {
        String body = """
                {"event": "payout.system_fail", "uuid": "u-3", "order_id": "o-3", "user_id": "user-1",
                 "status": "system_fail", "amount_requested": "1", "amount_to_receive": "1", "to_address": "0xdest",
                 "fee_info": {"fee_mode": "mix", "limit_currency": "USD"},
                 "sources": [
                   {"address": "0xaaa", "network": "ETH_MAINNET", "coin": "ETH", "amount_crypto": "1",
                    "need_refuel": false, "refuel_amount": "0", "estimated_fee": "0.0001", "estimated_fee_fiat": "0.30"}
                 ],
                 "service_operations": [],
                 "created_at": "2026-09-14T10:00:00Z", "completed_at": null, "error_reason": "MAX_RETRIES: PAYOUT_EXECUTE_FAILED"}
                """;
        PayoutWebhookEvent event = WebhookVerifier.parse(
                API_KEY, body.getBytes(StandardCharsets.UTF_8), sign(body), PayoutWebhookEvent.class);

        assertNull(event.confirmations());
        // A payout with no recorded depth leaves the key out; it must not break the decode.
        assertNull(event.requiredConfirmations());
        assertFalse(event.sources().get(0).has("confirmations"));
        assertEquals("MAX_RETRIES: PAYOUT_EXECUTE_FAILED", event.errorReason());
    }

    @Test
    void sweepWebhookCarriesTheCountAndTheDepthItWasHeldTo() throws Exception {
        String body = """
                {"event": "sweep.confirmed", "task_id": "task-1", "status": "completed",
                 "wallet_address": "0xdeposit", "to_address": "0xmaster", "network": "ETH_MAINNET",
                 "chain_family": "EVM", "asset_symbol": "USDT", "asset_contract": "0xdac17f958d2ee523a2206206994597c13d831ec7",
                 "asset_type": "token", "amount_raw": "250000000", "amount_human": "250",
                 "sweep_tx_hash": "0xsweep", "sweep_confirmations": 12, "required_confirmations": 12,
                 "confirmed_at": "2026-09-14T10:06:00Z", "type_work": "threshold", "total_fee_usd": "1.10"}
                """;
        SweepWebhookEvent event = WebhookVerifier.parse(
                API_KEY, body.getBytes(StandardCharsets.UTF_8), sign(body), SweepWebhookEvent.class);

        assertEquals(SweepWebhookEvent.EVENT_CONFIRMED, event.event());
        assertEquals(12, event.sweepConfirmations());
        assertEquals(12, event.requiredConfirmations());
        assertTrue(event.sweepConfirmations() >= event.requiredConfirmations());
    }

    @Test
    void sweepWebhookFromAnOlderSweepServiceHasNoDepthAndStillDecodes() throws Exception {
        // A sweep service built before sweeps waited for finality reports no depth, and the
        // platform leaves the key out rather than publishing 0.
        String body = """
                {"event": "sweep.confirmed", "task_id": "task-2", "status": "completed",
                 "wallet_address": "TDeposit", "to_address": "TMaster", "network": "TRON_MAINNET",
                 "chain_family": "TRON", "asset_symbol": "TRX", "asset_type": "native",
                 "amount_human": "40", "sweep_tx_hash": "abc", "sweep_confirmations": 1,
                 "confirmed_at": "2026-09-14T10:06:00Z", "type_work": "momentum"}
                """;
        SweepWebhookEvent event = WebhookVerifier.parse(
                API_KEY, body.getBytes(StandardCharsets.UTF_8), sign(body), SweepWebhookEvent.class);

        assertNull(event.requiredConfirmations());
        assertEquals(1, event.sweepConfirmations());
        assertEquals("task-2", event.taskId());
    }

    @Test
    void transactionWebhookCarriesTheCountAndTheThreshold() throws Exception {
        String confirmed = """
                {"event": "transaction.confirmed", "uuid": "t-1", "status": "confirmed", "network": "TRON_MAINNET",
                 "chain_family": "TRON", "type": "transfer", "from_address": "Tfrom", "to_address": "Tto",
                 "value": "1000000", "tx_hash": "abc", "confirmations": 19, "required_confirmations": 19,
                 "expires_at": "2026-09-14T11:00:00Z", "created_at": "2026-09-14T10:00:00Z",
                 "completed_at": "2026-09-14T10:02:00Z"}
                """;
        TransactionWebhookEvent ok = WebhookVerifier.parse(
                API_KEY, confirmed.getBytes(StandardCharsets.UTF_8), sign(confirmed), TransactionWebhookEvent.class);
        assertEquals(19, ok.confirmations());
        assertEquals(19, ok.requiredConfirmations());

        String expired = """
                {"event": "transaction.expired", "uuid": "t-2", "status": "expired", "network": "ETH_MAINNET",
                 "chain_family": "EVM", "type": "transfer", "from_address": "0xfrom", "to_address": "0xto",
                 "confirmations": 0, "required_confirmations": 12,
                 "expires_at": "2026-09-14T11:00:00Z", "created_at": "2026-09-14T10:00:00Z"}
                """;
        TransactionWebhookEvent gone = WebhookVerifier.parse(
                API_KEY, expired.getBytes(StandardCharsets.UTF_8), sign(expired), TransactionWebhookEvent.class);
        assertEquals(0, gone.confirmations());
        assertEquals(12, gone.requiredConfirmations());
    }

    @Test
    void parseThrowsOnBadSignature() {
        assertThrows(WebhookSignatureException.class, () -> WebhookVerifier.parse(
                API_KEY,
                "{\"event\":\"x\",\"uuid\":\"u\",\"order_id\":\"o\",\"status\":\"paid\"}"
                        .getBytes(StandardCharsets.UTF_8),
                "bad",
                PayoutWebhookEvent.class));
    }

    private static String sign(String body) throws Exception {
        return RequestSigner.sign(CanonicalJson.encode(CanonicalJson.MAPPER.readTree(body)), API_KEY);
    }
}
