package com.emailverify.sdk.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * Response from batch email verification (synchronous).
 */
public record BatchVerifyResponse(
    @JsonProperty("results") List<VerificationResult> results,
    @JsonProperty("total_emails") int totalEmails,
    @JsonProperty("valid_emails") int validEmails,
    @JsonProperty("invalid_emails") int invalidEmails,
    @JsonProperty("credits_used") int creditsUsed,
    @JsonProperty("process_time") long processTime
) {}
