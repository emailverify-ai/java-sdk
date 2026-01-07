package com.emailverify.sdk.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record BulkResultsResponse(
    @JsonProperty("job_id") String jobId,
    @JsonProperty("total") int total,
    @JsonProperty("limit") int limit,
    @JsonProperty("offset") int offset,
    @JsonProperty("results") List<BulkResultItem> results
) {}
