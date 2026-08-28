package com.cryptochief.processing;

import com.cryptochief.processing.models.CreatePayInRequest;
import com.cryptochief.processing.models.Environment;
import com.cryptochief.processing.models.PayInMode;
import com.cryptochief.processing.models.SweepFieldWrite;
import com.cryptochief.processing.models.SweepPolicyMode;
import com.cryptochief.processing.models.SweepSettings;
import com.cryptochief.processing.models.SweepSettingsQuery;
import com.cryptochief.processing.models.SweepStatus;
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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SweepsServiceTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final String SETTINGS_BODY = """
            {
              "wallet_address": "0xabc",
              "network_code": "ETH_MAINNET",
              "effective": {"type_work":"threshold","threshold_amount_usd":"250","fee_mode":"mix","source":"wallet"},
              "override": {"network_code":"","type_work":"threshold","threshold_amount_usd":"250","fee_mode":null,"source":"merchant","locked":false},
              "project_default": {"type_work":"momentum","fee_mode":"client"}
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
    void historyTellsABroadcastSweepFromASettledOne() throws Exception {
        server.enqueue(new MockResponse().setBody("""
                {"items":[
                  {"task_id":"t1","status":"broadcasted","wallet_address":"0xa","chain":"ETH_MAINNET",
                   "sweep_confirmations":2,"type_work":"threshold","total_fee_usd":"1.20"},
                  {"task_id":"t2","status":"completed","wallet_address":"0xb","chain":"ETH_MAINNET",
                   "sweep_confirmations":12,"completed_at":"2026-08-28T10:00:00Z","real_sweep_fee_usd":"0.98"}
                ],"meta":{"total":2,"page":1,"page_size":50}}
                """));

        var items = client.sweeps().history().items();

        var inFlight = items.get(0);
        var settled = items.get(1);
        assertEquals(SweepStatus.BROADCASTED, inFlight.status());
        assertEquals(2, inFlight.sweepConfirmations());
        // Still in flight: there is no settlement moment to report yet.
        assertNull(inFlight.completedAt());
        assertEquals("threshold", inFlight.typeWork());
        assertEquals("1.20", inFlight.totalFeeUsd());
        assertEquals(SweepStatus.COMPLETED, settled.status());
        assertEquals("2026-08-28T10:00:00Z", settled.completedAt());
        assertEquals("0.98", settled.realSweepFeeUsd());
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
