package com.cryptochief.processing;

import com.cryptochief.processing.http.Json;
import com.cryptochief.processing.models.BatchExecuteRequest;
import com.cryptochief.processing.models.BatchExecuteResponse;
import com.cryptochief.processing.models.BatchItemResult;
import com.fasterxml.jackson.databind.JsonNode;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BatchPayoutsServiceTest {

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
    void batchEstimateReadsOptionalHttpStatuses() throws Exception {
        server.enqueue(new MockResponse().setBody("""
                {"total":5,"ok":1,"failed":4,"total_estimated_fee_fiat":"0.10","items":[
                  {"index":0,"status":"ok","estimate":{"amount_requested":"1"}},
                  {"index":1,"status":"error","error":"FAILED_TO_ESTIMATE_FEE","http_status":400},
                  {"index":2,"status":"error","error":"FAILED_TO_ESTIMATE_FEE","http_status":502},
                  {"index":3,"status":"error","error":"INTERNAL_ERROR"},
                  {"index":4,"status":"error","error":"FUTURE_ERROR","http_status":599}
                ]}
                """));

        BatchExecuteResponse response = client.payouts().batchEstimate(new BatchExecuteRequest(null, List.of()));

        assertEquals("/v1/payout/batch/estimate", server.takeRequest().getPath());
        assertEquals("ok", response.items().get(0).status());
        assertHttpStatuses(response.items());
    }

    @Test
    void batchExecuteReadsOptionalHttpStatuses() throws Exception {
        server.enqueue(new MockResponse().setBody("""
                {"batch_uuid":"batch-1","total":5,"accepted":1,"rejected":4,"items":[
                  {"index":0,"order_id":"order-0","status":"queue","uuid":"payout-1",
                   "payout":{"uuid":"payout-1","status":"queue"}},
                  {"index":1,"order_id":"order-1","status":"error","error":"FAILED_TO_ESTIMATE_FEE","http_status":400},
                  {"index":2,"order_id":"order-2","status":"error","error":"FAILED_TO_ESTIMATE_FEE","http_status":502},
                  {"index":3,"order_id":"order-3","status":"error","error":"INTERNAL_ERROR"},
                  {"index":4,"order_id":"order-4","status":"error","error":"FUTURE_ERROR","http_status":599}
                ]}
                """));

        BatchExecuteResponse response = client.payouts().batchExecute(new BatchExecuteRequest(null, List.of()));

        assertEquals("/v1/payout/batch/execute", server.takeRequest().getPath());
        assertEquals("queue", response.items().get(0).status());
        assertEquals("payout-1", response.items().get(0).uuid());
        assertEquals("order-2", response.items().get(2).orderId());
        assertHttpStatuses(response.items());
    }

    private static void assertHttpStatuses(List<BatchItemResult> items) {
        assertEquals(5, items.size());
        assertNull(items.get(0).httpStatus());
        assertFalse(items.get(0).retryable());
        assertEquals(400, items.get(1).httpStatus());
        assertEquals("FAILED_TO_ESTIMATE_FEE", items.get(1).error());
        assertFalse(items.get(1).retryable());
        assertEquals(502, items.get(2).httpStatus());
        assertEquals("FAILED_TO_ESTIMATE_FEE", items.get(2).error());
        assertTrue(items.get(2).retryable());
        assertNull(items.get(3).httpStatus());
        assertEquals("INTERNAL_ERROR", items.get(3).error());
        assertFalse(items.get(3).retryable());
        assertEquals(599, items.get(4).httpStatus());
        assertFalse(items.get(4).retryable());
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(ints = {400, 408, 500, 502, 503, 504, 599})
    void onlyHttp502IsRetryable(Integer httpStatus) {
        BatchItemResult item = new BatchItemResult(0, "order-1", "error", null, "INTERNAL_ERROR", httpStatus);

        assertEquals(Integer.valueOf(502).equals(httpStatus), item.retryable());
    }

    @Test
    void serializationWritesHttpStatusAndOmitsItWhenAbsent() throws Exception {
        BatchItemResult queued = new BatchItemResult(0, "order-0", "queue", "payout-1", null);
        BatchExecuteResponse response = new BatchExecuteResponse("batch-1", 4, 1, 3, List.of(
                queued,
                new BatchItemResult(1, "order-1", "error", null, "INVALID_AMOUNT", 400),
                new BatchItemResult(2, "order-2", "error", null, "NETWORK_ERROR", 502),
                new BatchItemResult(3, "order-3", "error", null, "FUTURE_ERROR", 599)));

        JsonNode items = Json.MAPPER.readTree(Json.MAPPER.writeValueAsString(response)).get("items");

        assertNull(queued.httpStatus());
        assertFalse(queued.retryable());
        assertFalse(items.get(0).has("http_status"));
        assertEquals(400, items.get(1).get("http_status").intValue());
        assertEquals(502, items.get(2).get("http_status").intValue());
        assertEquals(599, items.get(3).get("http_status").intValue());
        for (JsonNode item : items) {
            assertFalse(item.has("httpStatus"));
            assertFalse(item.has("retryable"));
        }
    }
}
