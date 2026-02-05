package com.emailverify.sdk.model;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Response from health check endpoint.
 */
public record HealthResponse(
    @JsonProperty("status") String status,
    @JsonProperty("time") long time
) {}
