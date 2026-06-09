package com.cryptochief.processing.models;

import com.fasterxml.jackson.annotation.JsonProperty;

public record UuidRequest(@JsonProperty("uuid") String uuid) {}
