package com.emailverify.sdk.model;

import com.fasterxml.jackson.annotation.JsonProperty;

public record BulkJobResponse(
    @JsonProperty("job_id") String jobId,
    @JsonProperty("status") String status,
    @JsonProperty("total") int total,
    @JsonProperty("processed") int processed,
    @JsonProperty("valid") int valid,
    @JsonProperty("invalid") int invalid,
    @JsonProperty("unknown") int unknown,
    @JsonProperty("credits_used") int creditsUsed,
    @JsonProperty("created_at") String createdAt,
    @JsonProperty("completed_at") String completedAt,
    @JsonProperty("progress_percent") Integer progressPercent
) {}
