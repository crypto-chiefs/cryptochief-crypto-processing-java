package com.cryptochief.processing;

import com.cryptochief.processing.http.CanonicalJson;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CanonicalJsonTest {

    @Test
    void keysSortedRecursively() {
        Map<String, Object> nested = new LinkedHashMap<>();
        nested.put("zoo", List.of(3, 2, 1));
        nested.put("apple", "a");

        Map<String, Object> sample = new LinkedHashMap<>();
        sample.put("zeta", "z");
        sample.put("alpha", 1);
        sample.put("nested", nested);

        byte[] bytes = CanonicalJson.encode(sample);
        String text = new String(bytes, StandardCharsets.UTF_8);
        assertEquals("{\"alpha\":1,\"nested\":{\"apple\":\"a\",\"zoo\":[3,2,1]},\"zeta\":\"z\"}", text);
    }

    @Test
    void nullPayloadReturnsEmptyBytes() {
        assertEquals(0, CanonicalJson.encode(null).length);
    }

    @Test
    void emptyMapStaysEmpty() {
        assertEquals("{}", new String(CanonicalJson.encode(Map.of()), StandardCharsets.UTF_8));
    }
}
