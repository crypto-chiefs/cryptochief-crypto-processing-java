package com.cryptochief.processing;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/** One coin on one network. Empty fields match any. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record Asset(
        @JsonProperty("network") Chain network,
        @JsonProperty("coin") String coin
) {
    public Asset() {
        this(null, null);
    }
}
