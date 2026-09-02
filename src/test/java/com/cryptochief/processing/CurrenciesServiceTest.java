package com.cryptochief.processing;

import com.cryptochief.processing.models.CryptoCurrencies;
import com.cryptochief.processing.models.FiatCurrency;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CurrenciesServiceTest {

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
    void fiatsDecodesABareTopLevelArray() throws Exception {
        server.enqueue(new MockResponse().setBody("""
                [
                  {"code":"JMD","name":"Jamaican Dollar"},
                  {"code":"KYD","name":"Cayman Islands Dollar"},
                  {"code":"SEK","name":"Swedish Krona"}
                ]
                """));

        List<FiatCurrency> fiats = client.currencies().fiats();

        RecordedRequest recorded = server.takeRequest();
        assertEquals("POST", recorded.getMethod());
        assertEquals("/v1/currencies/fiats", recorded.getPath());
        assertEquals("mer_test", recorded.getHeader("Merchant"));
        assertEquals("{}", recorded.getBody().readUtf8());
        assertNotNull(recorded.getHeader("Signature"));

        assertEquals(3, fiats.size());
        assertEquals("JMD", fiats.get(0).code());
        assertEquals("Jamaican Dollar", fiats.get(0).name());
        assertEquals("SEK", fiats.get(2).code());
    }

    @Test
    void cryptosKeepsTheUnionAndThePerExchangeBreakdown() throws Exception {
        server.enqueue(new MockResponse().setBody("""
                {
                  "by_exchange": {
                    "binance": ["0G","1000CAT","1000SATS"],
                    "exmo": ["AAVE","ADA","BCH"]
                  },
                  "count": 2529,
                  "quote": "USDT",
                  "tickers": ["0G","1INCH","AAVE","ADA"]
                }
                """));

        CryptoCurrencies out = client.currencies().cryptos();

        RecordedRequest recorded = server.takeRequest();
        assertEquals("POST", recorded.getMethod());
        assertEquals("/v1/currencies/cryptos", recorded.getPath());
        assertEquals("{}", recorded.getBody().readUtf8());

        assertEquals("USDT", out.quote());
        assertEquals(2529, out.count());
        assertEquals(List.of("0G", "1INCH", "AAVE", "ADA"), out.tickers());
        // Which exchange carries which ticker, keyed by exchange name.
        assertEquals(2, out.byExchange().size());
        assertEquals(List.of("0G", "1000CAT", "1000SATS"), out.byExchange().get("binance"));
        assertEquals(List.of("AAVE", "ADA", "BCH"), out.byExchange().get("exmo"));
    }

    @Test
    void fiatsAnswersAnEmptyListForALiteralNullBody() throws Exception {
        // The service builds the array from a nil slice, which marshals as `null`, not `[]`.
        // A method that promises a List has to hand back a list either way: a caller writing
        // the obvious for-loop must not get an NPE the first time the platform has nothing.
        server.enqueue(new MockResponse().setBody("null"));

        List<FiatCurrency> fiats = client.currencies().fiats();

        assertEquals("/v1/currencies/fiats", server.takeRequest().getPath());
        assertNotNull(fiats);
        assertTrue(fiats.isEmpty());
    }

    @Test
    void cryptosAnswersEmptyCollectionsForALiteralNullBody() throws Exception {
        server.enqueue(new MockResponse().setBody("null"));

        CryptoCurrencies out = client.currencies().cryptos();

        assertEquals("/v1/currencies/cryptos", server.takeRequest().getPath());
        assertNotNull(out);
        assertNotNull(out.tickers());
        assertTrue(out.tickers().isEmpty());
        assertNotNull(out.byExchange());
        assertTrue(out.byExchange().isEmpty());
        assertEquals(0, out.count());
    }

    @Test
    void cryptosNormalizesNullsNestedInsideTheAnswer() throws Exception {
        // Same nil-slice story one level down: the object arrives, its collections do not.
        // `binance` is the third shape - a key the platform knows with no tickers under it,
        // which must stay a key rather than being dropped.
        server.enqueue(new MockResponse().setBody("""
                {
                  "tickers": null,
                  "by_exchange": {"binance": null},
                  "quote": "USDT",
                  "count": 0
                }
                """));

        CryptoCurrencies out = client.currencies().cryptos();

        assertEquals("/v1/currencies/cryptos", server.takeRequest().getPath());
        assertNotNull(out.tickers());
        assertTrue(out.tickers().isEmpty());
        assertEquals(1, out.byExchange().size());
        assertNotNull(out.byExchange().get("binance"));
        assertTrue(out.byExchange().get("binance").isEmpty());
        assertEquals("USDT", out.quote());
    }

    @Test
    void cryptosNormalizesAWhollyNullByExchangeMap() throws Exception {
        server.enqueue(new MockResponse().setBody("""
                {"tickers": ["USDT"], "by_exchange": null, "quote": "USDT", "count": 1}
                """));

        CryptoCurrencies out = client.currencies().cryptos();

        assertEquals("/v1/currencies/cryptos", server.takeRequest().getPath());
        assertEquals(List.of("USDT"), out.tickers());
        assertNotNull(out.byExchange());
        assertTrue(out.byExchange().isEmpty());
    }
}
