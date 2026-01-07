package com.emailverify.sdk.model;

import com.fasterxml.jackson.annotation.JsonProperty;

public record VerifyResponse(
    @JsonProperty("email") String email,
    @JsonProperty("status") String status,
    @JsonProperty("result") VerificationResult result,
    @JsonProperty("score") double score,
    @JsonProperty("reason") String reason,
    @JsonProperty("credits_used") int creditsUsed
) {}
