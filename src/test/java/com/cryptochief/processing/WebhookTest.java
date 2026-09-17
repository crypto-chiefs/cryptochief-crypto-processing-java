package com.cryptochief.processing;

import com.cryptochief.processing.exceptions.CryptoChiefException;
import com.cryptochief.processing.exceptions.DecodeException;
import com.cryptochief.processing.http.RequestSigner;
import com.cryptochief.processing.webhook.PayInWebhookEvent;
import com.cryptochief.processing.webhook.PayoutWebhookEvent;
import com.cryptochief.processing.webhook.SweepWebhookEvent;
import com.cryptochief.processing.webhook.TransactionWebhookEvent;
import com.cryptochief.processing.webhook.WebhookHeadersException;
import com.cryptochief.processing.webhook.WebhookOptions;
import com.cryptochief.processing.webhook.WebhookSignatureException;
import com.cryptochief.processing.webhook.WebhookTimestampException;
import com.cryptochief.processing.webhook.WebhookVerificationException;
import com.cryptochief.processing.webhook.WebhookVerifier;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WebhookTest {

    private static final String API_KEY = "secret";
    private static final String DELIVERY = "7c9e6679-7425-40de-944b-e07fc1f90ae7";
    private static final long NOW = 1789430400L;
    private static final WebhookOptions AT_NOW = WebhookOptions.defaults()
            .withClock(Clock.fixed(Instant.ofEpochSecond(NOW), ZoneOffset.UTC));

    private static byte[] utf8(String s) {
        return s.getBytes(StandardCharsets.UTF_8);
    }

    /** Headers of a webhook signed at {@code timestamp}. */
    private static Map<String, String> signed(String apiKey, long timestamp, byte[] body) {
        Map<String, String> headers = new HashMap<>();
        headers.put(WebhookVerifier.TIMESTAMP_HEADER, Long.toString(timestamp));
        headers.put(WebhookVerifier.DELIVERY_HEADER, DELIVERY);
        headers.put(WebhookVerifier.SIGNATURE_HEADER, RequestSigner.signWebhookV1(apiKey, timestamp, DELIVERY, body));
        return headers;
    }

    private static Map<String, String> signed(String body) {
        return signed(API_KEY, NOW, utf8(body));
    }

    @Test
    void headerNames() {
        assertEquals("X-Webhook-Delivery", WebhookVerifier.DELIVERY_HEADER);
        assertEquals("X-CC-Timestamp", WebhookVerifier.TIMESTAMP_HEADER);
        assertEquals("X-CC-Signature", WebhookVerifier.SIGNATURE_HEADER);
        assertEquals(Duration.ofSeconds(300), WebhookVerifier.DEFAULT_TOLERANCE);
        assertEquals(Duration.ofSeconds(300), WebhookOptions.defaults().tolerance());
    }

    @Test
    void verifiesRawBodyAndRejectsAnyChange() {
        String body = "{\"event\":\"payout.paid\",\"status\":\"paid\",\"uuid\":\"u\",\"order_id\":\"o\"}";
        Map<String, String> headers = signed(body);
        WebhookVerifier.verify(API_KEY, utf8(body), headers, AT_NOW);
        WebhookVerifier.verify(API_KEY, utf8(body), headers::get, AT_NOW);

        String reordered = "{\"status\":\"paid\",\"event\":\"payout.paid\",\"uuid\":\"u\",\"order_id\":\"o\"}";
        assertThrows(WebhookSignatureException.class,
                () -> WebhookVerifier.verify(API_KEY, utf8(reordered), headers, AT_NOW));
        String reformatted = body.replace(",", ", ");
        assertThrows(WebhookSignatureException.class,
                () -> WebhookVerifier.verify(API_KEY, utf8(reformatted), headers, AT_NOW));
        assertThrows(WebhookSignatureException.class,
                () -> WebhookVerifier.verify("other", utf8(body), headers, AT_NOW));
    }

    @Test
    void usesTheSystemClockByDefault() {
        long now = System.currentTimeMillis() / 1000L;
        byte[] body = utf8("{}");
        WebhookVerifier.verify(API_KEY, body, signed(API_KEY, now, body));
        assertThrows(WebhookTimestampException.class,
                () -> WebhookVerifier.verify(API_KEY, body, signed(API_KEY, now - 3600, body)));
    }

    @Test
    void toleranceIsConfigurable() {
        byte[] body = utf8("{}");
        Map<String, String> old = signed(API_KEY, NOW - 500, body);
        assertThrows(WebhookTimestampException.class, () -> WebhookVerifier.verify(API_KEY, body, old, AT_NOW));
        WebhookVerifier.verify(API_KEY, body, old, AT_NOW.withTolerance(Duration.ofSeconds(500)));
        assertThrows(WebhookTimestampException.class,
                () -> WebhookVerifier.verify(API_KEY, body, old, AT_NOW.withTolerance(Duration.ofSeconds(499))));

        assertThrows(IllegalArgumentException.class, () -> WebhookOptions.defaults().withTolerance(Duration.ZERO));
        assertThrows(IllegalArgumentException.class,
                () -> WebhookOptions.defaults().withTolerance(Duration.ofSeconds(-1)));
    }

    @Test
    void refusalsAreOrderedHeadersTimestampSignature() {
        byte[] body = utf8("{}");
        Map<String, String> stale = signed("other", NOW - 3600, body);
        assertThrows(WebhookTimestampException.class, () -> WebhookVerifier.verify(API_KEY, body, stale, AT_NOW));

        Map<String, String> staleWithoutDelivery = new HashMap<>(stale);
        staleWithoutDelivery.remove(WebhookVerifier.DELIVERY_HEADER);
        assertThrows(WebhookHeadersException.class,
                () -> WebhookVerifier.verify(API_KEY, body, staleWithoutDelivery, AT_NOW));
    }

    @Test
    void refusalsShareOneBaseClass() {
        assertTrue(CryptoChiefException.class.isAssignableFrom(WebhookVerificationException.class));
        for (Class<?> refusal : Set.of(WebhookHeadersException.class, WebhookTimestampException.class,
                WebhookSignatureException.class)) {
            assertTrue(WebhookVerificationException.class.isAssignableFrom(refusal), refusal.getName());
        }
    }

    /** A key of spaces and tabs is no key: it neither signs nor verifies, exactly like an empty one. */
    @Test
    void blankApiKeyIsAnArgumentError() {
        byte[] body = utf8("{}");
        Map<String, String> headers = signed("{}");
        for (String key : new String[] {null, "", " ", "\t", "  \t "}) {
            assertThrows(IllegalArgumentException.class,
                    () -> WebhookVerifier.verify(key, body, headers, AT_NOW), String.valueOf(key));
            assertThrows(IllegalArgumentException.class,
                    () -> WebhookVerifier.verify(key, body, headers::get, AT_NOW), String.valueOf(key));
            assertThrows(IllegalArgumentException.class,
                    () -> RequestSigner.signWebhookV1(key, NOW, DELIVERY, body), String.valueOf(key));
        }
        // A webhook actually signed with a blank key still does not verify under it.
        Map<String, String> blankSigned = new HashMap<>(headers);
        blankSigned.put(WebhookVerifier.SIGNATURE_HEADER, RequestSigner.HMAC_V1_PREFIX + hmacHex(" ", body));
        assertThrows(IllegalArgumentException.class,
                () -> WebhookVerifier.verify(" ", body, blankSigned, AT_NOW));
    }

    /** HMAC-SHA256 of the webhook string to sign, computed outside the SDK. */
    private static String hmacHex(String key, byte[] body) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            String stringToSign = RequestSigner.WEBHOOK_V1_SCOPE + "\n" + NOW + "\n" + DELIVERY + "\n"
                    + RequestSigner.bodySha256(body);
            return HexFormat.of().formatHex(mac.doFinal(stringToSign.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @Test
    void headerMapNamesAreCaseInsensitiveAndRepeatsCount() {
        byte[] body = utf8("{\"a\":1}");
        Map<String, String> headers = signed(API_KEY, NOW, body);

        Map<String, Object> lower = new HashMap<>();
        headers.forEach((k, v) -> lower.put(k.toLowerCase(Locale.ROOT), new String[] {v}));
        WebhookVerifier.verify(API_KEY, body, lower, AT_NOW);

        Map<String, Object> twoSpellings = new HashMap<>(lower);
        twoSpellings.put("X-CC-SIGNATURE", headers.get(WebhookVerifier.SIGNATURE_HEADER));
        assertThrows(WebhookHeadersException.class, () -> WebhookVerifier.verify(API_KEY, body, twoSpellings, AT_NOW));

        Map<String, Object> emptyList = new HashMap<>(lower);
        emptyList.put("x-webhook-delivery", List.of());
        assertThrows(WebhookHeadersException.class, () -> WebhookVerifier.verify(API_KEY, body, emptyList, AT_NOW));

        Map<String, Object> wrongType = new HashMap<>(lower);
        wrongType.put("x-cc-timestamp", NOW);
        assertThrows(IllegalArgumentException.class, () -> WebhookVerifier.verify(API_KEY, body, wrongType, AT_NOW));
    }

    /** Header-map names fold as the reference {@code strings.EqualFold} folds them. */
    @Test
    void headerMapNamesFoldLikeTheReference() {
        byte[] body = utf8("{\"event\":\"payout.paid\"}");
        Map<String, String> headers = signed(API_KEY, NOW, body);
        String ts = headers.get(WebhookVerifier.TIMESTAMP_HEADER);

        // U+0131 and U+0130 do not fold to 'i': the timestamp header is absent.
        for (String name : List.of("X-CC-Tımestamp", "X-CC-TİMESTAMP")) {
            Map<String, String> dotted = new HashMap<>(headers);
            dotted.remove(WebhookVerifier.TIMESTAMP_HEADER);
            dotted.put(name, ts);
            assertThrows(WebhookHeadersException.class, () -> WebhookVerifier.verify(API_KEY, body, dotted, AT_NOW),
                    name);

            // ... and is not a repeat of the real one.
            Map<String, String> both = new HashMap<>(headers);
            both.put(name, ts);
            WebhookVerifier.verify(API_KEY, body, both, AT_NOW);
        }

        // U+212A folds to 'k', U+017F to 's'.
        Map<String, String> folded = new HashMap<>();
        folded.put("X-CC-Timeſtamp", ts);
        folded.put("x-webhooK-delivery", DELIVERY);
        folded.put("X-CC-ſIGNATURE", headers.get(WebhookVerifier.SIGNATURE_HEADER));
        WebhookVerifier.verify(API_KEY, body, folded, AT_NOW);

        Map<String, String> repeated = new HashMap<>(headers);
        repeated.put("X-WebhooK-Delivery", DELIVERY);
        assertThrows(WebhookHeadersException.class, () -> WebhookVerifier.verify(API_KEY, body, repeated, AT_NOW));

        Map<String, String> otherNonAscii = new HashMap<>(headers);
        otherNonAscii.remove(WebhookVerifier.DELIVERY_HEADER);
        otherNonAscii.put("X-Wébhook-Delivery", DELIVERY);
        assertThrows(WebhookHeadersException.class,
                () -> WebhookVerifier.verify(API_KEY, body, otherNonAscii, AT_NOW));
    }

    @Test
    void timestampIsADecimalNumberWithoutALeadingZero() {
        byte[] body = utf8("{}");
        Map<String, String> headers = signed(API_KEY, NOW, body);
        WebhookVerifier.verify(API_KEY, body, headers, AT_NOW);

        // A leading zero leaves the signature valid for the canonical number, so it is refused by the format check.
        for (String padded : List.of("0" + NOW, "000" + NOW)) {
            headers.put(WebhookVerifier.TIMESTAMP_HEADER, padded);
            assertThrows(WebhookHeadersException.class,
                    () -> WebhookVerifier.verify(API_KEY, body, headers, AT_NOW), padded);
            assertThrows(WebhookHeadersException.class,
                    () -> WebhookVerifier.verify(API_KEY, body, headers::get, AT_NOW), padded);
        }

        headers.put(WebhookVerifier.TIMESTAMP_HEADER, "99999999999999999999");
        assertThrows(WebhookHeadersException.class, () -> WebhookVerifier.verify(API_KEY, body, headers, AT_NOW));

        headers.put(WebhookVerifier.TIMESTAMP_HEADER, "0");
        assertThrows(WebhookTimestampException.class, () -> WebhookVerifier.verify(API_KEY, body, headers, AT_NOW));

        headers.put(WebhookVerifier.TIMESTAMP_HEADER, " " + NOW + "\t");
        WebhookVerifier.verify(API_KEY, body, headers, AT_NOW);
    }

    @Test
    void signerRejectsWhatTheReceiverWouldRefuse() {
        byte[] body = utf8("{}");
        assertThrows(IllegalArgumentException.class, () -> RequestSigner.webhookV1StringToSign(0, DELIVERY, body));
        assertThrows(IllegalArgumentException.class, () -> RequestSigner.webhookV1StringToSign(-1, DELIVERY, body));
        assertThrows(IllegalArgumentException.class, () -> RequestSigner.webhookV1StringToSign(NOW, "", body));
        assertThrows(IllegalArgumentException.class, () -> RequestSigner.webhookV1StringToSign(NOW, "a.b", body));
        assertThrows(IllegalArgumentException.class, () -> RequestSigner.webhookV1StringToSign(NOW, "a\nb", body));
        assertThrows(IllegalArgumentException.class,
                () -> RequestSigner.webhookV1StringToSign(NOW, "a".repeat(129), body));
        assertEquals(RequestSigner.webhookV1StringToSign(NOW, DELIVERY, new byte[0]),
                RequestSigner.webhookV1StringToSign(NOW, DELIVERY, null));
    }

    @Test
    void parseReturnsTypedEvent() {
        String body = "{\"event\":\"payout.paid\",\"uuid\":\"u-1\",\"order_id\":\"o-1\",\"status\":\"paid\"}";
        PayoutWebhookEvent event = WebhookVerifier.parse(API_KEY, utf8(body), signed(body), AT_NOW,
                PayoutWebhookEvent.class);
        assertEquals("u-1", event.uuid());
        assertEquals("paid", event.status());

        Map<String, String> headers = signed(body);
        PayoutWebhookEvent viaLookup = WebhookVerifier.parse(API_KEY, utf8(body), headers::get, AT_NOW,
                PayoutWebhookEvent.class);
        assertEquals("u-1", viaLookup.uuid());
    }

    @Test
    void parseVerifiesBeforeDecoding() {
        String body = "{\"event\":\"x\",\"uuid\":\"u\",\"order_id\":\"o\",\"status\":\"paid\"}";
        Map<String, String> headers = signed(body);
        headers.put(WebhookVerifier.SIGNATURE_HEADER, "v1=" + "0".repeat(64));
        assertThrows(WebhookSignatureException.class,
                () -> WebhookVerifier.parse(API_KEY, utf8(body), headers, AT_NOW, PayoutWebhookEvent.class));
        assertThrows(WebhookHeadersException.class,
                () -> WebhookVerifier.parse(API_KEY, utf8("not json"), Map.of(), AT_NOW, PayoutWebhookEvent.class));
    }

    @Test
    void parseRejectsNullBody() {
        for (String body : List.of("null", " null ")) {
            Map<String, String> headers = signed(body);
            WebhookVerifier.verify(API_KEY, utf8(body), headers, AT_NOW);
            assertThrows(DecodeException.class,
                    () -> WebhookVerifier.parse(API_KEY, utf8(body), headers, AT_NOW, PayInWebhookEvent.class));
        }
    }

    @Test
    void payoutWebhookCarriesConfirmationsAtTheTopAndOnEveryItem() {
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
        PayoutWebhookEvent event = WebhookVerifier.parse(API_KEY, utf8(body), signed(body), AT_NOW,
                PayoutWebhookEvent.class);

        assertEquals(6, event.confirmations());
        // payout.paid goes out only once every source reached the network's depth.
        assertEquals(6, event.requiredConfirmations());
        assertEquals(15, event.sources().get(0).get("confirmations").asInt());
        assertEquals(6, event.sources().get(1).get("confirmations").asInt());
        assertEquals(20, event.serviceOperations().get(0).get("confirmations").asInt());
    }

    @Test
    void systemFailPayoutWebhookWithoutTransactionsHasNoConfirmations() {
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
        PayoutWebhookEvent event = WebhookVerifier.parse(API_KEY, utf8(body), signed(body), AT_NOW,
                PayoutWebhookEvent.class);

        assertNull(event.confirmations());
        // A payout with no recorded depth leaves the key out; it must not break the decode.
        assertNull(event.requiredConfirmations());
        assertFalse(event.sources().get(0).has("confirmations"));
        assertEquals("MAX_RETRIES: PAYOUT_EXECUTE_FAILED", event.errorReason());
    }

    @Test
    void sweepWebhookCarriesTheCountAndTheDepthItWasHeldTo() {
        String body = """
                {"event": "sweep.confirmed", "task_id": "task-1", "status": "completed",
                 "wallet_address": "0xdeposit", "to_address": "0xmaster", "network": "ETH_MAINNET",
                 "chain_family": "EVM", "asset_symbol": "USDT", "asset_contract": "0xdac17f958d2ee523a2206206994597c13d831ec7",
                 "asset_type": "token", "amount_raw": "250000000", "amount_human": "250",
                 "sweep_tx_hash": "0xsweep", "sweep_confirmations": 12, "required_confirmations": 12,
                 "confirmed_at": "2026-09-14T10:06:00Z", "type_work": "threshold", "total_fee_usd": "1.10"}
                """;
        SweepWebhookEvent event = WebhookVerifier.parse(API_KEY, utf8(body), signed(body), AT_NOW,
                SweepWebhookEvent.class);

        assertEquals(SweepWebhookEvent.EVENT_CONFIRMED, event.event());
        assertEquals(12, event.sweepConfirmations());
        assertEquals(12, event.requiredConfirmations());
        assertTrue(event.sweepConfirmations() >= event.requiredConfirmations());
    }

    @Test
    void sweepWebhookFromAnOlderSweepServiceHasNoDepthAndStillDecodes() {
        // A sweep service built before sweeps waited for finality reports no depth, and the
        // platform leaves the key out rather than publishing 0.
        String body = """
                {"event": "sweep.confirmed", "task_id": "task-2", "status": "completed",
                 "wallet_address": "TDeposit", "to_address": "TMaster", "network": "TRON_MAINNET",
                 "chain_family": "TRON", "asset_symbol": "TRX", "asset_type": "native",
                 "amount_human": "40", "sweep_tx_hash": "abc", "sweep_confirmations": 1,
                 "confirmed_at": "2026-09-14T10:06:00Z", "type_work": "momentum"}
                """;
        SweepWebhookEvent event = WebhookVerifier.parse(API_KEY, utf8(body), signed(body), AT_NOW,
                SweepWebhookEvent.class);

        assertNull(event.requiredConfirmations());
        assertEquals(1, event.sweepConfirmations());
        assertEquals("task-2", event.taskId());
    }

    @Test
    void transactionWebhookCarriesTheCountAndTheThreshold() {
        String confirmed = """
                {"event": "transaction.confirmed", "uuid": "t-1", "status": "confirmed", "network": "TRON_MAINNET",
                 "chain_family": "TRON", "type": "transfer", "from_address": "Tfrom", "to_address": "Tto",
                 "value": "1000000", "tx_hash": "abc", "confirmations": 19, "required_confirmations": 19,
                 "expires_at": "2026-09-14T11:00:00Z", "created_at": "2026-09-14T10:00:00Z",
                 "completed_at": "2026-09-14T10:02:00Z"}
                """;
        TransactionWebhookEvent ok = WebhookVerifier.parse(API_KEY, utf8(confirmed), signed(confirmed), AT_NOW,
                TransactionWebhookEvent.class);
        assertEquals(19, ok.confirmations());
        assertEquals(19, ok.requiredConfirmations());

        String expired = """
                {"event": "transaction.expired", "uuid": "t-2", "status": "expired", "network": "ETH_MAINNET",
                 "chain_family": "EVM", "type": "transfer", "from_address": "0xfrom", "to_address": "0xto",
                 "confirmations": 0, "required_confirmations": 12,
                 "expires_at": "2026-09-14T11:00:00Z", "created_at": "2026-09-14T10:00:00Z"}
                """;
        TransactionWebhookEvent gone = WebhookVerifier.parse(API_KEY, utf8(expired), signed(expired), AT_NOW,
                TransactionWebhookEvent.class);
        assertEquals(0, gone.confirmations());
        assertEquals(12, gone.requiredConfirmations());
    }

    @Test
    void payInBodyWithNullsDecodes() {
        String body = """
                {"memo": null, "mode": "crypto", "txid": null, "type": "PayIn",
                 "uuid": "f4f5ea1b-de1d-4f16-a251-8a8192b48629", "event": "invoice.confirming",
                 "status": "confirm_check", "is_test": false, "paid_at": null, "user_id": "1", "order_id": "19",
                 "url_error": "https://example.com/payment/failed?order=19&reason=expired",
                 "additional_data": {"cart": "<b>Кофе</b> & булочка", "items": 2},
                 "amount_crypto": "3", "confirmations": 0, "required_confirmations": 12}
                """;
        PayInWebhookEvent event = WebhookVerifier.parse(API_KEY, utf8(body), signed(body), AT_NOW,
                PayInWebhookEvent.class);
        assertEquals("invoice.confirming", event.event());
    }
}
