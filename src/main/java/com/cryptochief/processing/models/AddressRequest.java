package com.cryptochief.processing.models;

import com.fasterxml.jackson.annotation.JsonProperty;

public record AddressRequest(@JsonProperty("address") String address) {}
