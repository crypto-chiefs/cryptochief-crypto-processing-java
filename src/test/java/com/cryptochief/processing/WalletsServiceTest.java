package com.cryptochief.processing;

import com.cryptochief.processing.models.GenerateWalletRequest;
import com.cryptochief.processing.models.ListWalletsResponse;
import com.cryptochief.processing.models.Wallet;
import com.cryptochief.processing.models.WalletHistoryQuery;
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
              "callback_url": "https://your.app/webhooks/deposit",
              "label": "Acme Corp - EU"
            }
            """;

    /** The same shape with nothing bound: every field present, all three null. */
    private static final String UNBOUND_WALLET = """
            {
              "type": "transit",
              "address": "0xtransit",
              "chain_family": "EVM",
              "frozen": false,
              "master_wallet_address": null,
              "callback_url": null,
              "label": null
            }
            """;

    /** A master wallet: no callback URL of its own, but named like any other. */
    private static final String MASTER_WALLET = """
            {
              "type": "master",
              "address": "0xmaster",
              "chain_family": "EVM",
              "frozen": false,
              "master_wallet_address": null,
              "callback_url": null,
              "label": "Treasury"
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

    @Test
    void setLabelPostsTheAddressAndTheNameAndNothingElse() throws Exception {
        server.enqueue(new MockResponse().setBody(STATIC_WALLET));

        Wallet out = client.wallets().setLabel("0xstatic", "Acme Corp - EU");

        RecordedRequest recorded = server.takeRequest();
        assertEquals("POST", recorded.getMethod());
        assertEquals("/v1/wallets/label", recorded.getPath());
        assertEquals("mer_test", recorded.getHeader("Merchant"));
        assertEquals("{\"address\":\"0xstatic\",\"label\":\"Acme Corp - EU\"}",
                recorded.getBody().readUtf8());
        // The reply is the wallet as it stands afterwards, so the new name is visible
        // without asking again.
        assertEquals("Acme Corp - EU", out.label());
    }

    @Test
    void clearingTheLabelSendsAnEmptyStringRatherThanOmittingTheField() throws Exception {
        server.enqueue(new MockResponse().setBody(UNBOUND_WALLET));

        Wallet out = client.wallets().clearLabel("0xtransit");

        // "" is how this endpoint spells "clear it". Dropping the field - which the
        // canonical encoder does to nulls - would be a malformed request instead.
        assertEquals("{\"address\":\"0xtransit\",\"label\":\"\"}",
                server.takeRequest().getBody().readUtf8());
        // And the wallet comes back nameless: null, not the "" that was sent.
        assertNull(out.label());
    }

    @Test
    void nullLabelArgumentClearsRatherThanDisappearing() throws Exception {
        server.enqueue(new MockResponse().setBody(UNBOUND_WALLET));

        client.wallets().setLabel("0xtransit", null);

        JsonNode body = sentBody();
        assertTrue(body.has("label"));
        assertEquals("", body.get("label").asText());
    }

    @Test
    void everyWalletResponseCarriesTheLabel() throws Exception {
        // Generation, info, the list, and the two other wallet updates all answer with the
        // same wallet shape, and the name is part of it wherever it appears.
        server.enqueue(new MockResponse().setBody(STATIC_WALLET));
        assertEquals("Acme Corp - EU", client.wallets().generate(
                new GenerateWalletRequest(WalletType.STATIC, ChainFamily.EVM, "0xmaster", null))
                .label());

        server.enqueue(new MockResponse().setBody(STATIC_WALLET));
        assertEquals("Acme Corp - EU", client.wallets().info("0xstatic").label());

        server.enqueue(new MockResponse().setBody(STATIC_WALLET));
        assertEquals("Acme Corp - EU",
                client.wallets().rebindMaster("0xstatic", "0xnewmaster").label());

        server.enqueue(new MockResponse().setBody(STATIC_WALLET));
        assertEquals("Acme Corp - EU", client.wallets()
                .setCallbackUrl("0xstatic", "https://your.app/webhooks/deposit").label());

        server.enqueue(new MockResponse().setBody(
                "{\"items\":[" + MASTER_WALLET + "," + STATIC_WALLET + "," + UNBOUND_WALLET + "]}"));
        ListWalletsResponse listed = client.wallets().list();
        // Including the master wallet: a label names any wallet, not only the types that
        // take a callback URL.
        assertEquals("Treasury", listed.items().get(0).label());
        assertEquals("Acme Corp - EU", listed.items().get(1).label());
        assertNull(listed.items().get(2).label());
    }

    @Test
    void walletHistoryReadsBackTheSameOrdersPayInHistoryDoes() throws Exception {
        server.enqueue(new MockResponse().setBody("""
                {"items":[
                  {"uuid":"0a1b2c3d-4e5f-6789-abcd-ef0123456789","order_id":"invoice-1002",
                   "status":"paid","amount_crypto":"10.5","payment_coin":"USDT",
                   "payment_network":"TRON_MAINNET","to_address":"TQrY8bYc2yQ8sM8nJ1sZ9c2Zx7L2wq7pQb"}
                ],"meta":{"page":1,"page_size":20,"total":1}}
                """));

        var out = client.wallets().history("TQrY8bYc2yQ8sM8nJ1sZ9c2Zx7L2wq7pQb");

        RecordedRequest recorded = server.takeRequest();
        assertEquals("POST", recorded.getMethod());
        assertEquals("/v1/wallets/history", recorded.getPath());
        assertEquals("mer_test", recorded.getHeader("Merchant"));
        assertEquals("{\"address\":\"TQrY8bYc2yQ8sM8nJ1sZ9c2Zx7L2wq7pQb\"}",
                recorded.getBody().readUtf8());

        // The same order records as PayIn history, through the same types - a wallet is just
        // a narrower question about them.
        var order = out.items().get(0);
        assertEquals("invoice-1002", order.orderId());
        assertEquals("paid", order.status());
        assertTrue(order.succeeded());
        assertEquals("10.5", order.amountCrypto());
        assertEquals("USDT", order.paymentCoin());
        assertEquals(Chain.TRON_MAINNET, order.paymentNetwork());
        assertEquals(1, out.meta().page());
        assertEquals(20, out.meta().pageSize());
        assertEquals(1, out.meta().total());
    }

    @Test
    void walletHistorySendsTheDateWindowAndPagingUnderTheirWireNames() throws Exception {
        server.enqueue(new MockResponse().setBody("{\"items\":[],\"meta\":{\"page\":2,\"page_size\":50,\"total\":0}}"));

        client.wallets().history(new WalletHistoryQuery(
                "0xABCdef", "2026-08-01T00:00:00+00:00", "2026-08-31T23:59:59+00:00", 2, 50));

        assertEquals("{\"address\":\"0xABCdef\",\"date_from\":\"2026-08-01T00:00:00+00:00\","
                + "\"date_to\":\"2026-08-31T23:59:59+00:00\",\"page\":2,\"page_size\":50}",
                server.takeRequest().getBody().readUtf8());
    }

    @Test
    void anAddressYouDoNotOwnIsAnEmptyPageRatherThanAnError() throws Exception {
        server.enqueue(new MockResponse().setBody(
                "{\"items\":[],\"meta\":{\"page\":1,\"page_size\":20,\"total\":0}}"));

        var out = client.wallets().history("0xsomebodyelses");

        // No exception, no null items: an empty page says nothing about whether the address
        // exists, only that none of its orders are yours.
        assertTrue(out.items().isEmpty());
        assertEquals(0, out.meta().total());
    }

    @Test
    void aNullLabelDecodesAsNoNameRatherThanAsAnEmptyString() throws Exception {
        server.enqueue(new MockResponse().setBody(UNBOUND_WALLET));

        Wallet out = client.wallets().info("0xtransit");

        // The key is always present and null means "unnamed". A null has to read as
        // absence, not blow up the decode and not arrive as "".
        assertNull(out.label());
        assertEquals("0xtransit", out.address());
        assertEquals("transit", out.type());
    }
}
