package com.cryptochief.processing;

import com.cryptochief.processing.models.GenerateWalletRequest;
import com.cryptochief.processing.models.Wallet;
import com.cryptochief.processing.models.WalletType;
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

class WalletsServiceTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final String STATIC_WALLET = """
            {
              "type": "static",
              "address": "0xstatic",
              "chain_family": "EVM",
              "frozen": false,
              "master_wallet_address": "0xnewmaster",
              "callback_url": "https://your.app/webhooks/deposit"
            }
            """;

    /** The same shape with nothing bound: both fields present, both null. */
    private static final String UNBOUND_WALLET = """
            {
              "type": "transit",
              "address": "0xtransit",
              "chain_family": "EVM",
              "frozen": false,
              "master_wallet_address": null,
              "callback_url": null
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
    void generateSendsTheLabelUnderItsWireName() throws Exception {
        server.enqueue(new MockResponse().setBody(STATIC_WALLET));

        client.wallets().generate(new GenerateWalletRequest(
                WalletType.STATIC, ChainFamily.EVM, "0xmaster",
                "https://your.app/webhooks/deposit", "Acme Corp - EU"));

        RecordedRequest recorded = server.takeRequest();
        assertEquals("POST", recorded.getMethod());
        assertEquals("/v1/wallets/generate", recorded.getPath());
        assertEquals("{\"callback_url\":\"https://your.app/webhooks/deposit\","
                + "\"chain_family\":\"EVM\",\"label\":\"Acme Corp - EU\","
                + "\"master_wallet_address\":\"0xmaster\",\"wallet_type\":\"static\"}",
                recorded.getBody().readUtf8());
    }

    @Test
    void labelIsNotStaticOnly() throws Exception {
        server.enqueue(new MockResponse().setBody(STATIC_WALLET));

        // A master wallet is named the same way a static one is - the field is not tied to
        // the type that also carries a callback URL.
        client.wallets().generate(
                new GenerateWalletRequest(WalletType.MASTER, ChainFamily.EVM, null, null)
                        .withLabel("Treasury"));

        assertEquals("{\"chain_family\":\"EVM\",\"label\":\"Treasury\","
                + "\"wallet_type\":\"master\"}",
                server.takeRequest().getBody().readUtf8());
    }

    @Test
    void unsetLabelStaysOffTheWire() throws Exception {
        server.enqueue(new MockResponse().setBody(STATIC_WALLET));
        client.wallets().generate(
                new GenerateWalletRequest(WalletType.TRANSIT, ChainFamily.EVM, "0xmaster", null));
        assertFalse(sentBody().has("label"));

        server.enqueue(new MockResponse().setBody(STATIC_WALLET));
        // An empty label is the absence of a name, not a name that happens to be empty:
        // sending it would ask the platform to store one.
        client.wallets().generate(new GenerateWalletRequest(
                WalletType.TRANSIT, ChainFamily.EVM, "0xmaster", null, ""));
        assertFalse(sentBody().has("label"));
    }

    @Test
    void rebindMasterPostsBothAddresses() throws Exception {
        server.enqueue(new MockResponse().setBody(STATIC_WALLET));

        Wallet out = client.wallets().rebindMaster("0xstatic", "0xnewmaster");

        RecordedRequest recorded = server.takeRequest();
        assertEquals("POST", recorded.getMethod());
        assertEquals("/v1/wallets/rebind-master", recorded.getPath());
        assertEquals("mer_test", recorded.getHeader("Merchant"));
        assertEquals("{\"address\":\"0xstatic\",\"master_wallet_address\":\"0xnewmaster\"}",
                recorded.getBody().readUtf8());
        // The reply is the wallet as it stands afterwards, so the new binding is visible
        // without asking again.
        assertEquals("0xnewmaster", out.masterWalletAddress());
        assertEquals("static", out.type());
        assertEquals(ChainFamily.EVM, out.chainFamily());
        assertFalse(out.frozen());
    }

    @Test
    void setCallbackUrlPostsTheUrl() throws Exception {
        server.enqueue(new MockResponse().setBody(STATIC_WALLET));

        Wallet out = client.wallets()
                .setCallbackUrl("0xstatic", "https://your.app/webhooks/deposit");

        RecordedRequest recorded = server.takeRequest();
        assertEquals("POST", recorded.getMethod());
        assertEquals("/v1/wallets/callback-url", recorded.getPath());
        assertEquals("{\"address\":\"0xstatic\","
                + "\"callback_url\":\"https://your.app/webhooks/deposit\"}",
                recorded.getBody().readUtf8());
        assertEquals("https://your.app/webhooks/deposit", out.callbackUrl());
    }

    @Test
    void clearingSendsAnEmptyStringRatherThanOmittingTheField() throws Exception {
        server.enqueue(new MockResponse().setBody("""
                {"type":"static","address":"0xstatic","chain_family":"EVM","frozen":false,
                 "master_wallet_address":"0xmaster","callback_url":null}
                """));

        client.wallets().clearCallbackUrl("0xstatic");

        // "" is how this endpoint spells "clear it". Dropping the field - which the
        // canonical encoder does to nulls - would be a malformed request instead.
        assertEquals("{\"address\":\"0xstatic\",\"callback_url\":\"\"}",
                server.takeRequest().getBody().readUtf8());
    }

    @Test
    void nullCallbackUrlArgumentClearsRatherThanDisappearing() throws Exception {
        server.enqueue(new MockResponse().setBody(STATIC_WALLET));

        client.wallets().setCallbackUrl("0xstatic", null);

        JsonNode body = sentBody();
        assertTrue(body.has("callback_url"));
        assertEquals("", body.get("callback_url").asText());
    }

    @Test
    void nullMasterAndCallbackDecodeAsNullNotAsAFailure() throws Exception {
        server.enqueue(new MockResponse().setBody(UNBOUND_WALLET));

        Wallet out = client.wallets().rebindMaster("0xtransit", "0xmaster");

        // The platform always sends both keys and uses null for "no such value". A null
        // has to read as absence, not blow up the decode or arrive as "".
        assertNull(out.masterWalletAddress());
        assertNull(out.callbackUrl());
        assertEquals("transit", out.type());
        assertEquals("0xtransit", out.address());
        assertFalse(out.frozen());
    }
}
