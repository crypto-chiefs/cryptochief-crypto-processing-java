package com.cryptochief.processing;

import com.cryptochief.processing.http.HttpTransport;
import com.cryptochief.processing.models.BatchExecuteRequest;
import com.cryptochief.processing.models.ContractCall;
import com.cryptochief.processing.models.ConvertRequest;
import com.cryptochief.processing.models.CreatePayInRequest;
import com.cryptochief.processing.models.CreditsTopupRequest;
import com.cryptochief.processing.models.EstimatePayoutRequest;
import com.cryptochief.processing.models.ExecutePayoutRequest;
import com.cryptochief.processing.models.ExecuteTransactionRequest;
import com.cryptochief.processing.models.GenerateWalletRequest;
import com.cryptochief.processing.models.HistoryQuery;
import com.cryptochief.processing.models.PayInMode;
import com.cryptochief.processing.models.SelectAssetRequest;
import com.cryptochief.processing.models.SignTransactionRequest;
import com.cryptochief.processing.models.SolanaAccount;
import com.cryptochief.processing.models.StaticDepositHistoryQuery;
import com.cryptochief.processing.models.SweepFieldWrite;
import com.cryptochief.processing.models.SweepPolicyMode;
import com.cryptochief.processing.models.SweepWalletHistoryQuery;
import com.cryptochief.processing.models.TxType;
import com.cryptochief.processing.models.WalletType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Request bodies of methods with optional fields, compared as JSON values with the bodies of release 0.9.0.
 * Unset optional fields are absent, not {@code null}; {@code null} appears only where the caller put it into a
 * list or a JSON tree. {@code X-CC-Signature} is checked against the bytes received.
 */
class RequestBodyWireTest {

    private static final String MERCHANT = "mer_test";
    private static final String KEY = "secret-key";
    private static final Asset USDT_TRON = new Asset(Chain.TRON_MAINNET, "USDT");
    private static final ObjectMapper PARSER = new ObjectMapper();

    private interface Call {
        void run(CryptoChiefClient client, HttpTransport transport);
    }

    private record Case(String name, String expected, String response, Call call) {
        Case(String name, String expected, Call call) {
            this(name, expected, "{}", call);
        }
    }

    private MockWebServer server;
    private Options options;
    private CryptoChiefClient client;
    private HttpTransport transport;

    @BeforeEach
    void setUp() throws Exception {
        server = new MockWebServer();
        server.start();
        options = Options.builder()
                .merchantId(MERCHANT)
                .apiKey(KEY)
                .baseUrl(server.url("/").toString().replaceAll("/$", ""))
                .maxRetries(0)
                .build();
        client = new CryptoChiefClient(options);
        transport = new HttpTransport(options);
    }

    @AfterEach
    void tearDown() throws Exception {
        client.close();
        transport.http().dispatcher().executorService().shutdown();
        transport.http().connectionPool().evictAll();
        server.shutdown();
    }

    private static ExecutePayoutRequest payoutRequired() {
        return new ExecutePayoutRequest("o-1", null, Chain.TRON_MAINNET, "USDT", "10.50", "TXyz",
                null, null, null, null, null, null, null);
    }

    private static CreatePayInRequest cryptoOrder(String orderId) {
        return new CreatePayInRequest(orderId, null, PayInMode.CRYPTO, null, null, null, null, null, null, null,
                null, null, null, null, "10", USDT_TRON);
    }

    private static List<Case> cases() {
        String payout = "{\"amount\":\"10.50\",\"coin\":\"USDT\",\"network\":\"TRON_MAINNET\",\"order_id\":\"o-1\","
                + "\"to_address\":\"TXyz\"}";
        return List.of(
                new Case("payIns.create required only",
                        "{\"amount_crypto\":\"10\",\"asset\":{\"coin\":\"USDT\",\"network\":\"TRON_MAINNET\"},"
                                + "\"mode\":\"crypto\",\"order_id\":\"o-1\"}",
                        (c, t) -> c.payIns().create(cryptoOrder("o-1"))),
                new Case("payIns.create master wallet and environment null",
                        "{\"amount_crypto\":\"10\",\"asset\":{\"coin\":\"USDT\",\"network\":\"TRON_MAINNET\"},"
                                + "\"mode\":\"crypto\",\"order_id\":\"o-5\"}",
                        (c, t) -> c.payIns().create(cryptoOrder("o-5").withMasterWallet(null).withEnvironment(null))),
                new Case("payIns.create empty asset policy",
                        "{\"amount_fiat\":\"5\",\"assets\":{},\"currency\":\"EUR\",\"lifetime_sec\":600,"
                                + "\"mode\":\"fiat\",\"order_id\":\"o-3\",\"user_id\":\"u\"}",
                        (c, t) -> c.payIns().create(new CreatePayInRequest("o-3", "u", PayInMode.FIAT, null, 600,
                                null, null, null, null, null, "5", "EUR", null, new AssetsPolicy(), null, null))),
                new Case("payIns.selectAsset optional null",
                        "{\"uuid\":\"u-1\"}",
                        (c, t) -> c.payIns().selectAsset(new SelectAssetRequest("u-1", null, null, null))),
                new Case("payIns.history default",
                        "{}",
                        (c, t) -> c.payIns().history()),
                new Case("payouts.estimate required only",
                        "{\"amount\":\"10\",\"coin\":\"USDT\",\"network\":\"TRON_MAINNET\",\"to_address\":\"TX\"}",
                        (c, t) -> c.payouts().estimate(EstimatePayoutRequest.of(Chain.TRON_MAINNET, "USDT", "10", "TX"))),
                new Case("payouts.estimate null list elements",
                        "{\"amount\":\"10\",\"auto_convert_policy\":{\"allow\":[null]},\"coin\":\"USDT\","
                                + "\"from_addresses\":[\"TA\",null],\"network\":\"TRON_MAINNET\",\"to_address\":\"TX\"}",
                        (c, t) -> c.payouts().estimate(new EstimatePayoutRequest(Chain.TRON_MAINNET, "USDT", "10",
                                "TX", Arrays.asList("TA", null), null, null,
                                new AssetsPolicy(Arrays.asList((Asset) null), null), null, null))),
                new Case("payouts.execute required only",
                        payout,
                        (c, t) -> c.payouts().execute(payoutRequired())),
                new Case("payouts.batchExecute without callback",
                        "{\"items\":[" + payout + "]}",
                        (c, t) -> c.payouts().batchExecute(new BatchExecuteRequest(null, List.of(payoutRequired())))),
                new Case("payouts.history page only",
                        "{\"page\":1}",
                        (c, t) -> c.payouts().history(new HistoryQuery(1, null, null, null, null, null, null))),
                new Case("sweeps.updateSettings nothing supplied",
                        "{\"address\":\"TA\"}",
                        (c, t) -> c.sweeps().updateSettings("TA", null, null, null, null, null)),
                new Case("sweeps.updateSettings inherit every field",
                        "{\"address\":\"TA\",\"fields\":[\"type_work\",\"threshold_amount_usd\",\"fee_mode\","
                                + "\"gas_source\"],\"network_code\":\"TRON_MAINNET\"}",
                        (c, t) -> c.sweeps().updateSettings("TA", Chain.TRON_MAINNET, SweepFieldWrite.inherit(),
                                SweepFieldWrite.inherit(), SweepFieldWrite.inherit(), SweepFieldWrite.inherit())),
                new Case("sweeps.updateSettings set, inherit and leave alone",
                        "{\"address\":\"TA\",\"fields\":[\"type_work\",\"threshold_amount_usd\",\"gas_source\"],"
                                + "\"type_work\":\"momentum\"}",
                        (c, t) -> c.sweeps().updateSettings("TA", null, SweepFieldWrite.set(SweepPolicyMode.MOMENTUM),
                                SweepFieldWrite.inherit(), null, SweepFieldWrite.set(null))),
                new Case("sweeps.updateGasSource inherit",
                        "{\"address\":\"TA\",\"fields\":[\"gas_source\"]}",
                        (c, t) -> c.sweeps().updateGasSource("TA", SweepFieldWrite.inherit())),
                new Case("sweeps.settings project default",
                        "{}",
                        (c, t) -> c.sweeps().settings()),
                new Case("sweeps.force without network",
                        "{\"address\":\"TA\"}",
                        (c, t) -> c.sweeps().force("TA", null)),
                new Case("sweeps.walletHistory filters null",
                        "{\"address\":\"TA\"}",
                        (c, t) -> c.sweeps().walletHistory(
                                SweepWalletHistoryQuery.forAddress("TA").withStatus(null).withSearch(null))),
                new Case("staticDeposits.history address only",
                        "{\"address\":\"TA\"}",
                        (c, t) -> c.staticDeposits().history(
                                new StaticDepositHistoryQuery("TA", null, null, null, null, null, null, null))),
                new Case("wallets.generate without optional fields",
                        "{\"chain_family\":\"EVM\",\"wallet_type\":\"master\"}",
                        (c, t) -> c.wallets().generate(
                                new GenerateWalletRequest(WalletType.MASTER, ChainFamily.EVM, null, null))),
                new Case("wallets.generate empty label",
                        "{\"chain_family\":\"EVM\",\"master_wallet_address\":\"0xM\",\"wallet_type\":\"transit\"}",
                        (c, t) -> c.wallets().generate(
                                new GenerateWalletRequest(WalletType.TRANSIT, ChainFamily.EVM, "0xM", null, ""))),
                new Case("wallets.setLabel null",
                        "{\"address\":\"TA\",\"label\":\"\"}",
                        (c, t) -> c.wallets().setLabel("TA", null)),
                new Case("wallets.setCallbackUrl null",
                        "{\"address\":\"TA\",\"callback_url\":\"\"}",
                        (c, t) -> c.wallets().setCallbackUrl("TA", null)),
                new Case("wallets.rebindMaster null master",
                        "{\"address\":\"TA\"}",
                        (c, t) -> c.wallets().rebindMaster("TA", null)),
                new Case("transactions.execute without signed tx",
                        "{\"uuid\":\"t-1\"}",
                        (c, t) -> c.transactions().execute(ExecuteTransactionRequest.of("t-1"))),
                new Case("transactions.sign null call and optional fields",
                        "{\"calls\":[{\"accounts\":[{\"is_signer\":true,\"is_writable\":false,\"pubkey\":\"A\"}],"
                                + "\"data\":\"AAE=\",\"to\":\"P\"},{\"bounce\":false,\"to\":\"Q\",\"value\":\"0\"},null],"
                                + "\"from_address\":\"So1\",\"network\":\"SOLANA_MAINNET\",\"type\":\"contract\"}",
                        (c, t) -> c.transactions().sign(new SignTransactionRequest(Chain.SOLANA_MAINNET, "So1",
                                TxType.CONTRACT, null, null, null,
                                Arrays.asList(new ContractCall("P", null, "AAE=",
                                                List.of(new SolanaAccount("A", true, false)), null),
                                        new ContractCall("Q", "0", null, null, false), null),
                                null))),
                new Case("transactions.signAnchorCall optional null",
                        "{\"calls\":[{\"data\":\"r69tHw2Ym+0=\",\"to\":\"Prog\"}],\"from_address\":\"So1\","
                                + "\"network\":\"SOLANA_MAINNET\",\"type\":\"contract\"}",
                        (c, t) -> c.transactions().signAnchorCall(Chain.SOLANA_MAINNET, "So1", "Prog", "initialize",
                                null, null, null)),
                new Case("transactions.signTonCall optional null",
                        "{\"calls\":[{\"data\":\"CQ==\",\"to\":\"EQCxE6mUtQJKFnGfaROTKOt1lZbDiiX1kCixRv7Nw2Id_sDs\","
                                + "\"value\":\"0\"}],\"from_address\":\"EQCxE6mUtQJKFnGfaROTKOt1lZbDiiX1kCixRv7Nw2Id_sDs\","
                                + "\"network\":\"TON_MAINNET\",\"type\":\"contract\"}",
                        (c, t) -> c.transactions().signTonCall(Chain.TON_MAINNET,
                                "EQCxE6mUtQJKFnGfaROTKOt1lZbDiiX1kCixRv7Nw2Id_sDs",
                                "EQCxE6mUtQJKFnGfaROTKOt1lZbDiiX1kCixRv7Nw2Id_sDs", new byte[]{9}, null, null, null)),
                new Case("credits.topup without urls",
                        "{\"amount\":\"25\",\"currency\":\"USDT\"}",
                        (c, t) -> c.credits().topup(CreditsTopupRequest.of("25", "USDT"))),
                new Case("credits.topup null request",
                        "",
                        (c, t) -> c.credits().topup(null)),
                new Case("currencies.fiatToCrypto without provider",
                        "{\"amount\":\"100\",\"from\":\"USD\",\"to\":\"USDT\"}",
                        (c, t) -> c.currencies().fiatToCrypto(new ConvertRequest(null, "USD", "USDT", "100"))),
                new Case("blockchain.walletBalance without contracts",
                        "{\"addresses\":[\"0xabc\"],\"chain\":\"ETH_MAINNET\"}",
                        "[]",
                        (c, t) -> c.blockchain().walletBalance(Chain.ETH_MAINNET, List.of("0xabc"), null)),
                new Case("transport map with null values",
                        "{\"a\":\"x\",\"inner\":{\"d\":1.5},\"list\":[\"y\",null]}",
                        (c, t) -> {
                            Map<String, Object> inner = new HashMap<>();
                            inner.put("c", null);
                            inner.put("d", 1.50);
                            Map<String, Object> body = new HashMap<>();
                            body.put("a", "x");
                            body.put("b", null);
                            body.put("inner", inner);
                            body.put("list", new ArrayList<>(Arrays.asList("y", null)));
                            t.send("/v1/any", body, JsonNode.class);
                        }),
                new Case("transport json tree with null",
                        "{\"a\":null,\"arr\":[null,{\"k\":null}],\"z\":1}",
                        (c, t) -> {
                            ObjectNode body = JsonNodeFactory.instance.objectNode();
                            body.put("z", 1);
                            body.putNull("a");
                            body.putArray("arr").addNull().addObject().putNull("k");
                            t.send("/v1/any", body, JsonNode.class);
                        }));
    }

    @TestFactory
    Stream<DynamicTest> bodiesMatchRelease090() {
        return cases().stream().map(cs -> DynamicTest.dynamicTest(cs.name(), () -> {
            server.enqueue(new MockResponse().setBody(cs.response()));
            cs.call().run(client, transport);
            RecordedRequest request = server.takeRequest(5, TimeUnit.SECONDS);
            assertNotNull(request);
            byte[] body = request.getBody().readByteArray();
            if (cs.expected().isEmpty()) {
                assertEquals(0, body.length);
            } else {
                assertEquals(PARSER.readTree(cs.expected()), PARSER.readTree(body));
            }
            assertNull(request.getHeader("Signature"));
            assertEquals(hmacV1(request, body), request.getHeader("X-CC-Signature"));
        }));
    }

    /** Integers beyond 2^53 and decimals are sent with their exact digits. */
    @Test
    void numbersAreSentExactly() throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("long", 9007199254740993L);
        body.put("big_integer", new BigInteger("123456789012345678901234567890"));
        body.put("big_decimal", new BigDecimal("0.10000000000000000000001"));
        body.put("max_long", Long.MAX_VALUE);
        server.enqueue(new MockResponse().setBody("{}"));
        transport.send("/v1/any", body, JsonNode.class);
        RecordedRequest request = server.takeRequest(5, TimeUnit.SECONDS);
        assertNotNull(request);
        byte[] sent = request.getBody().readByteArray();
        assertEquals("{" + String.join(",", "\"big_decimal\":0.10000000000000000000001",
                "\"big_integer\":123456789012345678901234567890", "\"long\":9007199254740993",
                "\"max_long\":9223372036854775807") + "}", new String(sent, StandardCharsets.UTF_8));
        assertEquals(hmacV1(request, sent), request.getHeader("X-CC-Signature"));
    }

    private static String hmacV1(RecordedRequest request, byte[] body) throws Exception {
        String query = request.getRequestUrl().encodedQuery();
        String stringToSign = String.join("\n",
                "CC-HMAC-SHA256-REQ-V1",
                request.getHeader("X-CC-Timestamp"),
                request.getHeader("X-CC-Nonce"),
                request.getMethod(),
                request.getRequestUrl().encodedPath(),
                query == null ? "" : query,
                MERCHANT,
                "",
                HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(body)));
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(KEY.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return "v1=" + HexFormat.of().formatHex(mac.doFinal(stringToSign.getBytes(StandardCharsets.UTF_8)));
    }
}
