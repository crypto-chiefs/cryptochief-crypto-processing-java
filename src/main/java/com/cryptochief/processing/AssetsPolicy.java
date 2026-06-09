package com.cryptochief.processing;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/** Allow / exclude filter over {@link Asset}. */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record AssetsPolicy(
        @JsonProperty("allow") List<Asset> allow,
        @JsonProperty("exclude") List<Asset> exclude
) {
    public AssetsPolicy() {
        this(List.of(), List.of());
    }
}
