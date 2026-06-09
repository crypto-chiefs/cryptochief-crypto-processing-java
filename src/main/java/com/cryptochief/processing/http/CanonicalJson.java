package com.cryptochief.processing.http;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

/** Canonical JSON encoder used by request signing and webhook verification. */
public final class CanonicalJson {

    /** Shared mapper configured for canonical output. */
    public static final ObjectMapper MAPPER = JsonMapper.builder()
            .serializationInclusion(JsonInclude.Include.NON_NULL)
            .configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true)
            .configure(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY, true)
            .configure(MapperFeature.SORT_CREATOR_PROPERTIES_BY_DECLARATION_ORDER, false)
            .configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, true)
            .build();

    public static final byte[] EMPTY = new byte[0];

    private CanonicalJson() {}

    /** Serialise {@code value} to canonical UTF-8 bytes. {@code null} returns an empty array. */
    public static byte[] encode(Object value) {
        if (value == null) return EMPTY;
        try {
            Object subject = (value instanceof JsonNode node) ? sortKeysRecursive(node) : value;
            return MAPPER.writeValueAsBytes(subject);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("cryptochief: canonicalise: " + e.getMessage(), e);
        }
    }

    /** Decode UTF-8 bytes from raw JSON to a target type. */
    public static <T> T decode(byte[] body, Class<T> type) throws JsonProcessingException {
        return MAPPER.readValue(new String(body, StandardCharsets.UTF_8), type);
    }

    /** Returns a new tree with every {@link ObjectNode}'s keys sorted alphabetically. */
    public static JsonNode sortKeysRecursive(JsonNode element) {
        if (element instanceof ObjectNode obj) {
            ObjectNode out = MAPPER.createObjectNode();
            List<String> keys = new ArrayList<>();
            Iterator<String> it = obj.fieldNames();
            while (it.hasNext()) keys.add(it.next());
            Collections.sort(keys);
            for (String k : keys) {
                out.set(k, sortKeysRecursive(obj.get(k)));
            }
            return out;
        }
        if (element instanceof ArrayNode arr) {
            ArrayNode out = MAPPER.createArrayNode();
            for (JsonNode item : arr) out.add(sortKeysRecursive(item));
            return out;
        }
        return element;
    }
}
