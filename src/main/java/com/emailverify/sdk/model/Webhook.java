package com.emailverify.sdk.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record Webhook(
    @JsonProperty("id") String id,
    @JsonProperty("url") String url,
    @JsonProperty("events") List<String> events,
    @JsonProperty("created_at") String createdAt
) {}
