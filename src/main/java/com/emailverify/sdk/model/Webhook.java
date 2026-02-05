package com.emailverify.sdk.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * Webhook configuration.
 */
public record Webhook(
    @JsonProperty("id") String id,
    @JsonProperty("url") String url,
    @JsonProperty("events") List<String> events,
    @JsonProperty("secret") String secret,
    @JsonProperty("is_active") boolean isActive,
    @JsonProperty("created_at") String createdAt,
    @JsonProperty("updated_at") String updatedAt
) {}
