package com.cryptochief.processing.models;

import com.cryptochief.processing.Chain;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * The body of {@code /v1/sweeps/settings/update}.
 *
 * <p>{@code fields} names what this call is writing. A field listed there but absent from
 * the body is being cleared - which is the only way to drop one field while keeping the
 * others. Built by {@link com.cryptochief.processing.services.SweepsService}; callers pass
 * {@link SweepFieldWrite} values instead.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record SweepSettingsUpdateRequest(
        @JsonProperty("address") String address,
        @JsonProperty("network_code") Chain networkCode,
        @JsonProperty("fields") List<String> fields,
        @JsonProperty("type_work") String typeWork,
        @JsonProperty("threshold_amount_usd") String thresholdAmountUsd,
        @JsonProperty("fee_mode") String feeMode
) {}
