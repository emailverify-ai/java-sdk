package com.emailverify.sdk.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Map;

public record BulkResultItem(
    @JsonProperty("email") String email,
    @JsonProperty("status") String status,
    @JsonProperty("result") Map<String, Object> result,
    @JsonProperty("score") double score
) {}
