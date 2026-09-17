package com.cryptochief.processing.http;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;

/** Jackson mapper shared by request bodies, responses, webhook events and the TON RPC client. */
public final class Json {

    /**
     * Omits {@code null} properties and {@code null} map values; writes properties and map entries in key
     * order. Integers, {@code BigInteger} and {@code BigDecimal} are written exactly.
     */
    public static final ObjectMapper MAPPER = JsonMapper.builder()
            .serializationInclusion(JsonInclude.Include.NON_NULL)
            .configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true)
            .configure(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY, true)
            .configure(MapperFeature.SORT_CREATOR_PROPERTIES_BY_DECLARATION_ORDER, false)
            .configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, true)
            .build();

    private Json() {}
}
