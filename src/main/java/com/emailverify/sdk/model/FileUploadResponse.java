package com.emailverify.sdk.model;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Response from file upload for verification.
 */
public record FileUploadResponse(
    @JsonProperty("task_id") String taskId,
    @JsonProperty("status") String status,
    @JsonProperty("message") String message,
    @JsonProperty("file_name") String fileName,
    @JsonProperty("file_size") long fileSize,
    @JsonProperty("estimated_count") int estimatedCount,
    @JsonProperty("unique_emails") int uniqueEmails,
    @JsonProperty("total_rows") int totalRows,
    @JsonProperty("email_column") String emailColumn,
    @JsonProperty("status_url") String statusUrl,
    @JsonProperty("created_at") String createdAt
) {}
