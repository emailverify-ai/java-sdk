package com.emailverify.sdk.model;

import com.fasterxml.jackson.annotation.JsonProperty;

public record CreditsResponse(
    @JsonProperty("available") int available,
    @JsonProperty("used") int used,
    @JsonProperty("total") int total,
    @JsonProperty("plan") String plan,
    @JsonProperty("resets_at") String resetsAt,
    @JsonProperty("rate_limit") RateLimit rateLimit
) {}
