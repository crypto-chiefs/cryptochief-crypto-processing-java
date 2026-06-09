package com.cryptochief.processing.ton;

import com.cryptochief.processing.exceptions.NetworkException;
import com.cryptochief.processing.http.CanonicalJson;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/** Internal TON RPC client used by the high-level TON helpers. */
public final class TonRpcClient {

    private static final MediaType APPLICATION_JSON = MediaType.get("application/json");

    private final String merchantId;
    private final String baseUrl;
    private final OkHttpClient http;
    private final String userAgent;
    private final ConcurrentHashMap<String, String> jettonWalletCache = new ConcurrentHashMap<>();

    public TonRpcClient(String merchantId, String baseUrl, OkHttpClient http, String userAgent) {
        this.merchantId = Objects.requireNonNull(merchantId, "merchantId");
        this.baseUrl = trimTrailingSlash(baseUrl);
        this.http = http;
        this.userAgent = userAgent;
    }

    public String lookupJettonWallet(String jettonMaster, String owner) {
        if (jettonMaster == null || jettonMaster.isEmpty()) {
            throw new IllegalArgumentException("jettonMaster is required");
        }
        if (owner == null || owner.isEmpty()) {
            throw new IllegalArgumentException("owner is required");
        }
        String cacheKey = owner + "|" + jettonMaster;
        String cached = jettonWalletCache.get(cacheKey);
        if (cached != null) return cached;

        try {
            String addr = resolveViaRunMethod(jettonMaster, owner);
            jettonWalletCache.put(cacheKey, addr);
            return addr;
        } catch (Exception ignored) {
        }
        String viaIndex = resolveViaIndex(jettonMaster, owner);
        jettonWalletCache.put(cacheKey, viaIndex);
        return viaIndex;
    }

    public boolean hasJettonWallet(String jettonMaster, String owner) {
        try {
            HttpUrl url = jettonWalletsUrl(jettonMaster, owner);
            String body = get(url.toString());
            JsonNode root = CanonicalJson.MAPPER.readTree(body);
            JsonNode wallets = root.get("jetton_wallets");
            return wallets != null && wallets.isArray() && wallets.size() > 0;
        } catch (Exception e) {
            return false;
        }
    }

    private String resolveViaRunMethod(String jettonMaster, String owner) {
        Cell ownerCell = new CellBuilder().storeAddress(TonAddress.parse(owner)).endCell();
        String ownerBoc = Base64.getEncoder().encodeToString(ownerCell.toBoc());

        ObjectNode payload = CanonicalJson.MAPPER.createObjectNode();
        payload.put("address", jettonMaster);
        payload.put("method", "get_wallet_address");
        ArrayNode stack = payload.putArray("stack");
        ObjectNode slice = stack.addObject();
        slice.put("type", "slice");
        slice.put("value", ownerBoc);
        try {
            String text = post("/runGetMethod", CanonicalJson.MAPPER.writeValueAsString(payload));
            JsonNode response = CanonicalJson.MAPPER.readTree(text);
            JsonNode exitCode = response.get("exit_code");
            if (exitCode != null && exitCode.isInt() && exitCode.asInt() != 0) {
                throw new NetworkException("ton/runGetMethod: exit_code=" + exitCode.asInt());
            }
        } catch (IOException e) {
            throw new NetworkException("ton/runGetMethod: " + e.getMessage(), e);
        }
        return resolveViaIndex(jettonMaster, owner);
    }

    private String resolveViaIndex(String jettonMaster, String owner) {
        HttpUrl url = jettonWalletsUrl(jettonMaster, owner);
        String body = get(url.toString());
        try {
            JsonNode root = CanonicalJson.MAPPER.readTree(body);
            JsonNode wallets = root.get("jetton_wallets");
            if (wallets == null || !wallets.isArray() || wallets.isEmpty()) {
                throw new NetworkException(
                        "no Jetton wallet for owner " + owner + " on master " + jettonMaster);
            }
            String rawAddr = wallets.get(0).path("address").asText("");
            JsonNode book = root.get("address_book");
            if (book != null && book.isObject() && book.has(rawAddr)) {
                String friendly = book.get(rawAddr).path("user_friendly").asText("");
                if (!friendly.isEmpty()) return friendly;
            }
            return rawAddr;
        } catch (IOException e) {
            throw new NetworkException("ton/jetton-wallets: " + e.getMessage(), e);
        }
    }

    private HttpUrl jettonWalletsUrl(String jettonMaster, String owner) {
        String base = baseUrl + "/ton-v3/" + merchantId + "/jetton/wallets";
        HttpUrl parsed = HttpUrl.parse(base);
        if (parsed == null) throw new NetworkException("ton: invalid base URL " + base);
        return parsed.newBuilder()
                .addQueryParameter("owner_address", owner)
                .addQueryParameter("jetton_address", jettonMaster)
                .addQueryParameter("limit", "1")
                .build();
    }

    private String get(String url) {
        Request req = new Request.Builder()
                .url(url)
                .header("Accept", "application/json")
                .header("User-Agent", userAgent == null ? "" : userAgent)
                .get()
                .build();
        return execute(req);
    }

    private String post(String path, String body) {
        String url = baseUrl + "/ton-v3/" + merchantId + (path.startsWith("/") ? path : "/" + path);
        Request req = new Request.Builder()
                .url(url)
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .header("User-Agent", userAgent == null ? "" : userAgent)
                .post(RequestBody.create(body.getBytes(StandardCharsets.UTF_8), APPLICATION_JSON))
                .build();
        return execute(req);
    }

    private String execute(Request req) {
        try (Response response = http.newCall(req).execute()) {
            ResponseBody respBody = response.body();
            String text = respBody == null ? "" : respBody.string();
            if (!response.isSuccessful()) {
                throw new NetworkException("ton: " + req.method() + " " + req.url() + ": HTTP "
                        + response.code() + ": " + text.substring(0, Math.min(text.length(), 256)));
            }
            return text;
        } catch (IOException e) {
            throw new NetworkException("ton: " + req.method() + " " + req.url() + ": " + e.getMessage(), e);
        }
    }

    private static String trimTrailingSlash(String s) {
        int end = s.length();
        while (end > 0 && s.charAt(end - 1) == '/') end--;
        return s.substring(0, end);
    }
}
