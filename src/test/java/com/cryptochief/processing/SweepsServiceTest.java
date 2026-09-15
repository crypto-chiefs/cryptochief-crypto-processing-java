package com.cryptochief.processing;

import com.cryptochief.processing.models.CreatePayInRequest;
import com.cryptochief.processing.models.Environment;
import com.cryptochief.processing.models.PayInMode;
import com.cryptochief.processing.models.Sweep;
import com.cryptochief.processing.models.SweepFieldWrite;
import com.cryptochief.processing.models.SweepGasSource;
import com.cryptochief.processing.models.SweepHistoryQuery;
import com.cryptochief.processing.models.SweepPolicyMode;
import com.cryptochief.processing.models.SweepSettings;
import com.cryptochief.processing.models.SweepSettingsQuery;
import com.cryptochief.processing.models.SweepStatus;
import com.cryptochief.processing.models.SweepWalletHistoryQuery;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SweepsServiceTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final String SETTINGS_BODY = """
            {
              "wallet_address": "0xabc",
              "network_code": "ETH_MAINNET",
              "effective": {"type_work":"threshold","threshold_amount_usd":"250","fee_mode":"mix","gas_source":"rented","source":"wallet"},
              "override": {"network_code":"","type_work":"threshold","threshold_amount_usd":"250","fee_mode":null,"gas_source":null,"source":"merchant","locked":false},
              "project_default": {"type_work":"momentum","fee_mode":"client","gas_source":"native"}
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

    private JsonNode sentBody() throws Exception {
        RecordedRequest req = server.takeRequest();
        return MAPPER.readTree(req.getBody().readUtf8());
    }

    @Test
    void settingsReturnsThreeDistinguishableLayers() throws Exception {
        server.enqueue(new MockResponse().setBody(SETTINGS_BODY));

        SweepSettings out = client.sweeps().settings(SweepSettingsQuery.forAddress("0xabc"));

        assertEquals(SweepPolicyMode.THRESHOLD, out.effective().typeWork());
        assertEquals("250", out.effective().thresholdAmountUsd());
        assertEquals("wallet", out.effective().source());
        // An inherited field reads as null on the override while the effective policy
        // still has a value. That difference is the point of the three-layer shape.
        assertNull(out.override().feeMode());
        assertEquals("threshold", out.override().typeWork());
        assertFalse(out.override().locked());
        assertEquals(SweepPolicyMode.MOMENTUM, out.projectDefault().typeWork());
    }

    @Test
    void updateWritesOnlyTheFieldsItWasGiven() throws Exception {
        server.enqueue(new MockResponse().setBody(SETTINGS_BODY));

        client.sweeps().updateSettings("0xabc",
                SweepFieldWrite.set(SweepPolicyMode.THRESHOLD),
                SweepFieldWrite.set("250"),
                null);

        JsonNode body = sentBody();
        assertEquals("threshold", body.get("type_work").asText());
        assertEquals("250", body.get("threshold_amount_usd").asText());
        // Sending fee_mode at all would rewrite it; untouched means absent.
        assertFalse(body.has("fee_mode"));
        assertEquals(2, body.get("fields").size());
        assertEquals("type_work", body.get("fields").get(0).asText());
        assertEquals("threshold_amount_usd", body.get("fields").get(1).asText());
    }

    @Test
    void inheritNamesTheFieldAndSendsNoValue() throws Exception {
        server.enqueue(new MockResponse().setBody(SETTINGS_BODY));

        client.sweeps().updateSettings("0xabc", SweepFieldWrite.inherit(), null, null);

        JsonNode body = sentBody();
        // The API's way of saying "inherit this again": named, with no value. null cannot
        // express it because it already means "not supplied".
        assertEquals(1, body.get("fields").size());
        assertEquals("type_work", body.get("fields").get(0).asText());
        assertFalse(body.has("type_work"));
    }

    @Test
    void historyTellsABroadcastSweepFromASettledOneAndAFailedOne() throws Exception {
        server.enqueue(new MockResponse().setBody("""
                {"items":[
                  {"task_id":"t1","status":"broadcasted","wallet_address":"0xa","chain":"ETH_MAINNET",
                   "sweep_confirmations":2,"required_confirmations":12,"completed_at":"2026-08-28T09:58:00Z","type_work":"threshold","total_fee_usd":"1.20"},
                  {"task_id":"t2","status":"completed","wallet_address":"0xb","chain":"ETH_MAINNET",
                   "sweep_confirmations":12,"required_confirmations":12,"completed_at":"2026-08-28T10:00:00Z","real_sweep_fee_usd":"0.98"},
                  {"task_id":"t3","status":"failed","wallet_address":"0xc","chain":"ETH_MAINNET",
                   "sweep_confirmations":0,"required_confirmations":12,"completed_at":"2026-08-28T10:04:00Z"}
                ],"meta":{"total":3,"page":1,"page_size":50}}
                """));

        var items = client.sweeps().history().items();

        var inFlight = items.get(0);
        var settled = items.get(1);
        var failed = items.get(2);
        assertEquals(SweepStatus.BROADCASTED, inFlight.status());
        // In a block but not yet at the network's depth: a non-zero count is NOT settlement.
        assertEquals(2, inFlight.sweepConfirmations());
        assertEquals(12, inFlight.requiredConfirmations());
        assertTrue(inFlight.sweepConfirmations() < inFlight.requiredConfirmations());
        // completedAt() is the send time, so a sweep still settling carries one too.
        assertNotNull(inFlight.completedAt());
        assertEquals("threshold", inFlight.typeWork());
        assertEquals("1.20", inFlight.totalFeeUsd());
        assertEquals(SweepStatus.COMPLETED, settled.status());
        assertEquals(12, settled.sweepConfirmations());
        assertEquals(12, settled.requiredConfirmations());
        assertEquals("2026-08-28T10:00:00Z", settled.completedAt());
        assertEquals("0.98", settled.realSweepFeeUsd());

        // completedAt() is present on all three rows; only the status tells them apart.
        assertEquals(SweepStatus.FAILED, failed.status());
        assertNotNull(failed.completedAt());
        assertEquals(0, failed.sweepConfirmations());
    }

    /** The settlement rule from the README and the {@code Sweep} doc comment. */
    private static boolean settled(Sweep s) {
        return SweepStatus.COMPLETED.equals(s.status())
                && s.sweepConfirmations() != null && s.sweepConfirmations() > 0;
    }

    @Test
    void aCompletedSweepWithZeroConfirmationsIsNotSettled() throws Exception {
        server.enqueue(new MockResponse().setBody("""
                {"items":[
                  {"task_id":"t1","status":"completed","wallet_address":"0xa","chain":"ETH_MAINNET",
                   "sweep_confirmations":0,"required_confirmations":12,"completed_at":"2026-06-01T10:00:00Z"},
                  {"task_id":"t2","status":"broadcasted","wallet_address":"0xb","chain":"ETH_MAINNET",
                   "sweep_confirmations":3,"required_confirmations":12,"completed_at":"2026-09-14T10:00:00Z"},
                  {"task_id":"t3","status":"completed","wallet_address":"0xc","chain":"ETH_MAINNET",
                   "sweep_confirmations":12,"required_confirmations":12,"completed_at":"2026-09-14T10:00:00Z"},
                  {"task_id":"t4","status":"completed","wallet_address":"0xd","chain":"ETH_MAINNET",
                   "required_confirmations":12,"completed_at":"2026-06-01T10:00:00Z"}
                ],"meta":{"total":4,"page":1,"page_size":20}}
                """));

        var items = client.sweeps().history().items();

        // An older record: completed with a zero count was never observed in a block.
        assertEquals(SweepStatus.COMPLETED, items.get(0).status());
        assertEquals(0, items.get(0).sweepConfirmations());
        assertFalse(settled(items.get(0)));
        // A count above zero without completed is still in flight.
        assertFalse(settled(items.get(1)));
        assertTrue(settled(items.get(2)));
        // An absent count does not throw and is not settled.
        assertNull(items.get(3).sweepConfirmations());
        assertFalse(settled(items.get(3)));
    }

    @Test
    void finalityWithoutABlockCountReadsAsTheRequiredDepth() throws Exception {
        // A network that reports "final" without counting blocks is published with the count
        // equal to the depth - no longer the 4294967295 older history rows carried.
        server.enqueue(new MockResponse().setBody("""
                {"items":[
                  {"task_id":"t1","status":"completed","wallet_address":"So1ana","chain":"SOLANA_MAINNET",
                   "sweep_confirmations":32,"required_confirmations":32,"completed_at":"2026-09-14T10:00:00Z"}
                ],"meta":{"total":1,"page":1,"page_size":20}}
                """));

        var sweep = client.sweeps().walletHistory(SweepWalletHistoryQuery.forAddress("So1ana")).items().get(0);

        assertEquals(SweepStatus.COMPLETED, sweep.status());
        assertEquals(sweep.requiredConfirmations(), sweep.sweepConfirmations());
    }

    @Test
    void historyFromAPlatformWithoutTheDepthStillDecodes() throws Exception {
        server.enqueue(new MockResponse().setBody("""
                {"items":[
                  {"task_id":"t1","status":"completed","wallet_address":"0xa","chain":"ETH_MAINNET",
                   "sweep_confirmations":1,"completed_at":"2026-09-14T10:00:00Z"}
                ],"meta":{"total":1,"page":1,"page_size":20}}
                """));

        var sweep = client.sweeps().history().items().get(0);

        assertNull(sweep.requiredConfirmations());
        assertEquals(1, sweep.sweepConfirmations());
    }

    @Test
    void aNullGasSourceOnAnOverrideMeansUndecidedNotSwitchedOff() throws Exception {
        server.enqueue(new MockResponse().setBody(SETTINGS_BODY));

        SweepSettings out = client.sweeps().settings(SweepSettingsQuery.forAddress("0xabc"));

        // The override does not decide this field, so it is inherited - which is not the
        // same as "no energy is rented". Reading it as a value would invert the meaning.
        assertNull(out.override().gasSource());
        // The effective layer is where the concrete answer lives, and it is always concrete.
        assertEquals(SweepGasSource.RENTED, out.effective().gasSource());
        assertEquals(SweepGasSource.NATIVE, out.projectDefault().gasSource());
    }

    @Test
    void updateSettingsSendsGasSourceAndNamesItInTheMask() throws Exception {
        server.enqueue(new MockResponse().setBody(SETTINGS_BODY));

        client.sweeps().updateGasSource("TR7NHq",
                SweepFieldWrite.set(SweepGasSource.NATIVE));

        JsonNode body = sentBody();
        assertEquals("native", body.get("gas_source").asText());
        assertEquals(1, body.get("fields").size());
        assertEquals("gas_source", body.get("fields").get(0).asText());
        // Only the gas source is being written; the mode, threshold and fee mode stay as
        // they are, which means staying off the wire.
        assertFalse(body.has("type_work"));
        assertFalse(body.has("fee_mode"));
    }

    @Test
    void inheritingGasSourceNamesItInTheMaskWithNoValue() throws Exception {
        server.enqueue(new MockResponse().setBody(SETTINGS_BODY));

        client.sweeps().updateGasSource("TR7NHq", SweepFieldWrite.inherit());

        JsonNode body = sentBody();
        // Named, with no value: that mask is the only way to drop this one field and keep
        // the others. Sending "rented" instead would pin the default rather than clear it.
        assertEquals(1, body.get("fields").size());
        assertEquals("gas_source", body.get("fields").get(0).asText());
        assertFalse(body.has("gas_source"));
    }

    @Test
    void notWritingGasSourceIsNotWritingNative() throws Exception {
        server.enqueue(new MockResponse().setBody(SETTINGS_BODY));

        client.sweeps().updateSettings("TR7NHq",
                SweepFieldWrite.set(SweepPolicyMode.MOMENTUM), null, null);

        JsonNode body = sentBody();
        // A wallet that never chose one gets the platform default, "rented" - energy
        // supplied and billed to API credits. Leaving the field alone does not opt out of
        // that; only sending "native" does.
        assertFalse(body.has("gas_source"));
        assertEquals(1, body.get("fields").size());
        assertEquals("type_work", body.get("fields").get(0).asText());
    }

    @Test
    void historyFiltersReachTheWireUnderTheirOwnNames() throws Exception {
        server.enqueue(new MockResponse().setBody("{\"items\":[],\"meta\":{\"total\":0,\"page\":1,\"page_size\":20}}"));

        client.sweeps().history(SweepHistoryQuery.empty()
                .withStatus(SweepStatus.SKIPPED)
                .withSearch("0x77EDde3213b70c9dd224C874c28f41B23B070f65"));

        RecordedRequest recorded = server.takeRequest();
        assertEquals("/v1/sweeps/history", recorded.getPath());
        assertEquals("{\"search\":\"0x77EDde3213b70c9dd224C874c28f41B23B070f65\","
                + "\"status\":\"skipped\"}", recorded.getBody().readUtf8());
    }

    @Test
    void anAbsentStatusFilterIncludesEverySweepIncludingSkipped() throws Exception {
        server.enqueue(new MockResponse().setBody("""
                {"items":[
                  {"task_id":"t1","status":"skipped","wallet_address":"0xa","chain":"ETH_MAINNET"},
                  {"task_id":"t2","status":"completed","wallet_address":"0xb","chain":"ETH_MAINNET"}
                ],"meta":{"total":2,"page":1,"page_size":20}}
                """));

        var items = client.sweeps().history().items();

        // No status filter is not "the interesting ones": skipped sweeps come back too, and
        // a skipped sweep is a normal outcome rather than a failure.
        assertEquals("{}", server.takeRequest().getBody().readUtf8());
        assertEquals(SweepStatus.SKIPPED, items.get(0).status());
        assertEquals(SweepStatus.COMPLETED, items.get(1).status());
    }

    @Test
    void walletHistoryKeepsTheAddressAndAddsTheFilters() throws Exception {
        server.enqueue(new MockResponse().setBody("{\"items\":[],\"meta\":{\"total\":0,\"page\":1,\"page_size\":20}}"));

        client.sweeps().walletHistory(SweepWalletHistoryQuery.forAddress("0xabc")
                .withStatus(SweepStatus.FAILED)
                .withSearch("898cdbd0-d583-4089-9c53-15f5ca9b53dc"));

        RecordedRequest recorded = server.takeRequest();
        assertEquals("/v1/sweeps/wallet/history", recorded.getPath());
        // The address stays required and separate; search matches the hashes and task id.
        assertEquals("{\"address\":\"0xabc\","
                + "\"search\":\"898cdbd0-d583-4089-9c53-15f5ca9b53dc\","
                + "\"status\":\"failed\"}", recorded.getBody().readUtf8());
    }

    @Test
    void environmentReachesTheWireAndIsOmittedWhenUnset() throws Exception {
        server.enqueue(new MockResponse().setBody("{\"uuid\":\"u1\",\"order_id\":\"o1\",\"status\":\"pending\"}"));

        client.payIns().create(new CreatePayInRequest(
                "o1", "u", PayInMode.FIAT, null, null, null, null, null, null, null,
                "10", "USD", null, null, null, null)
                .withEnvironment(Environment.TESTNET));
        assertEquals("testnet", sentBody().get("environment").asText());

        server.enqueue(new MockResponse().setBody("{\"uuid\":\"u2\",\"order_id\":\"o2\",\"status\":\"pending\"}"));
        client.payIns().create(new CreatePayInRequest(
                "o2", "u", PayInMode.FIAT, null, null, null, null, null, null, null,
                "10", "USD", null, null, null, null));
        // Unset must stay off the wire: an empty string is a value the platform has to
        // reject, not the "use the project default" the caller meant.
        assertTrue(!sentBody().has("environment"));
    }
}
