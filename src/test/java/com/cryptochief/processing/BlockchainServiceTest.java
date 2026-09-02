package com.cryptochief.processing;

import com.cryptochief.processing.models.AvailableContract;
import com.cryptochief.processing.models.SupportedBlockchain;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BlockchainServiceTest {

    /** The catalogue as the platform sends it: a native coin and a token, on both environments. */
    private static final String CATALOGUE = """
            {
              "items": [
                {"network":"ETH_MAINNET","coin":"ETH","contract":"","chain_family":"EVM",
                 "type":"native","is_test":false,"decimals":18},
                {"network":"TRON_MAINNET","coin":"USDT",
                 "contract":"TR7NHqjeKQxGTCi8q8ZY4pL8otSzgjLj6t","chain_family":"TRON",
                 "type":"token","is_test":false,"decimals":6},
                {"network":"SOLANA_DEVNET","coin":"SOL","contract":"","chain_family":"SOLANA",
                 "type":"native","is_test":true,"decimals":9}
              ]
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
    void blockchainsDecodesABareTopLevelArray() throws Exception {
        // This endpoint answers with an array, not {"items":[...]}. A decoder written for the
        // envelope compiles fine and fails only against the live API, so the array shape is
        // what the test feeds it.
        server.enqueue(new MockResponse().setBody("""
                [
                  {"name":"ETH_MAINNET","type":"evm"},
                  {"name":"ETH_SEPOLIA","type":"evm"},
                  {"name":"TRON_MAINNET","type":"tron"},
                  {"name":"SOLANA_MAINNET","type":"solana"}
                ]
                """));

        List<SupportedBlockchain> chains = client.blockchain().blockchains();

        RecordedRequest recorded = server.takeRequest();
        assertEquals("POST", recorded.getMethod());
        assertEquals("/v1/blockchains/list", recorded.getPath());
        assertEquals("mer_test", recorded.getHeader("Merchant"));
        // Nothing to filter by, but the empty body is still signed like any other request.
        assertEquals("{}", recorded.getBody().readUtf8());
        assertNotNull(recorded.getHeader("Signature"));

        assertEquals(4, chains.size());
        assertEquals(Chain.ETH_MAINNET, chains.get(0).name());
        // The scanner spells the protocol family its own way, in lower case - not the
        // upper-case ChainFamily the rest of the API uses.
        assertEquals("evm", chains.get(0).type());
        assertEquals(Chain.TRON_MAINNET, chains.get(2).name());
        assertEquals("tron", chains.get(2).type());
        assertEquals("solana", chains.get(3).type());
    }

    @Test
    void blockchainsAnswersAnEmptyListForALiteralNullBody() throws Exception {
        // The service builds the array from a nil slice, which marshals as `null`, not `[]`.
        // A method that promises a List has to hand back a list either way: a caller writing
        // the obvious for-loop must not get an NPE the first time the platform has nothing.
        server.enqueue(new MockResponse().setBody("null"));

        List<SupportedBlockchain> chains = client.blockchain().blockchains();

        assertEquals("/v1/blockchains/list", server.takeRequest().getPath());
        assertNotNull(chains);
        assertTrue(chains.isEmpty());
    }

    @Test
    void contractsListPostsAnEmptyBodyAndKeepsChainFamilyAndIsTest() throws Exception {
        server.enqueue(new MockResponse().setBody(CATALOGUE));

        List<AvailableContract> items = client.blockchain().contractsList().items();

        RecordedRequest recorded = server.takeRequest();
        assertEquals("POST", recorded.getMethod());
        assertEquals("/v1/blockchain/contracts/list", recorded.getPath());
        // Platform-wide: there is nothing to filter by project.
        assertEquals("{}", recorded.getBody().readUtf8());

        assertEquals(3, items.size());
        // Both fields the SDK used to drop on the floor survive the decode.
        assertEquals(ChainFamily.EVM, items.get(0).chainFamily());
        assertEquals(ChainFamily.TRON, items.get(1).chainFamily());
        assertEquals(ChainFamily.SOLANA, items.get(2).chainFamily());
        assertFalse(items.get(0).isTest());
        assertFalse(items.get(1).isTest());
        // The one thing that tells a worthless payment from a real one.
        assertTrue(items.get(2).isTest());
    }

    @Test
    void aNativeCoinsEmptyContractStaysAnEmptyString() throws Exception {
        server.enqueue(new MockResponse().setBody(CATALOGUE));

        List<AvailableContract> items = client.blockchain().contractsList().items();

        // "" is how the platform says "no contract, this is the chain's own coin". It must
        // not arrive as null, and it must not fail the decode.
        assertEquals("", items.get(0).contract());
        assertEquals("", items.get(2).contract());
        assertEquals("native", items.get(0).type());
        assertEquals("TR7NHqjeKQxGTCi8q8ZY4pL8otSzgjLj6t", items.get(1).contract());
        assertEquals("token", items.get(1).type());
        assertEquals(18, items.get(0).decimals());
        assertEquals(6, items.get(1).decimals());
    }

    @Test
    void theProjectCatalogueCarriesTheSameTwoFields() throws Exception {
        // chain_family and is_test are present on both catalogues, and both decode through
        // the one shared item type.
        server.enqueue(new MockResponse().setBody(CATALOGUE));

        List<AvailableContract> items = client.blockchain().contractsAvailable().items();

        assertEquals("/v1/blockchain/contracts/available", server.takeRequest().getPath());
        assertEquals(ChainFamily.EVM, items.get(0).chainFamily());
        assertTrue(items.get(2).isTest());
        assertEquals(Chain.SOLANA_DEVNET, items.get(2).network());
    }
}
