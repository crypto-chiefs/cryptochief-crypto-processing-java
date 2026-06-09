package com.cryptochief.processing.models;

import com.cryptochief.processing.Chain;
import com.fasterxml.jackson.annotation.JsonProperty;

public record NetworkRequest(@JsonProperty("network") Chain network) {}
