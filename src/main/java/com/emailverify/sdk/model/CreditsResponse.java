package com.emailverify.sdk.model;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Response containing credit balance information.
 */
public record CreditsResponse(
    @JsonProperty("account_id") String accountId,
    @JsonProperty("api_key_id") String apiKeyId,
    @JsonProperty("api_key_name") String apiKeyName,
    @JsonProperty("credits_balance") int creditsBalance,
    @JsonProperty("credits_consumed") int creditsConsumed,
    @JsonProperty("credits_added") int creditsAdded,
    @JsonProperty("last_updated") String lastUpdated
) {}
