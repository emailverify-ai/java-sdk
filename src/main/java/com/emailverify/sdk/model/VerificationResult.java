package com.emailverify.sdk.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * Result of an email verification operation.
 */
public record VerificationResult(
    @JsonProperty("email") String email,
    @JsonProperty("status") Status status,
    @JsonProperty("score") double score,
    @JsonProperty("is_deliverable") boolean isDeliverable,
    @JsonProperty("is_disposable") boolean isDisposable,
    @JsonProperty("is_catchall") boolean isCatchall,
    @JsonProperty("is_role") boolean isRole,
    @JsonProperty("is_free") boolean isFree,
    @JsonProperty("domain") String domain,
    @JsonProperty("domain_age") Integer domainAge,
    @JsonProperty("mx_records") List<String> mxRecords,
    @JsonProperty("domain_reputation") DomainReputation domainReputation,
    @JsonProperty("smtp_check") boolean smtpCheck,
    @JsonProperty("reason") String reason,
    @JsonProperty("suggestion") String suggestion,
    @JsonProperty("response_time") Integer responseTime,
    @JsonProperty("credits_used") int creditsUsed
) {}
