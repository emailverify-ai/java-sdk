package com.emailverify.sdk.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;

/**
 * Wrapper for API responses with the standard format:
 * {success, code, message, data}
 */
public record ApiResponse(
    @JsonProperty("success") boolean success,
    @JsonProperty("code") String code,
    @JsonProperty("message") String message,
    @JsonProperty("data") JsonNode data
) {}
