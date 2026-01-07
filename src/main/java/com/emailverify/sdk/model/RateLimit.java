package com.emailverify.sdk.model;

import com.fasterxml.jackson.annotation.JsonProperty;

public record RateLimit(
    @JsonProperty("requests_per_hour") int requestsPerHour,
    @JsonProperty("remaining") int remaining
) {}
