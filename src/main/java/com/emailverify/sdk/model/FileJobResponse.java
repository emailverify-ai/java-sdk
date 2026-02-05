package com.emailverify.sdk.model;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Response from file verification job status.
 */
public record FileJobResponse(
    @JsonProperty("job_id") String jobId,
    @JsonProperty("status") String status,
    @JsonProperty("file_name") String fileName,
    @JsonProperty("total_emails") int totalEmails,
    @JsonProperty("processed_emails") int processedEmails,
    @JsonProperty("progress_percent") int progressPercent,
    @JsonProperty("valid_emails") int validEmails,
    @JsonProperty("invalid_emails") int invalidEmails,
    @JsonProperty("unknown_emails") int unknownEmails,
    @JsonProperty("role_emails") int roleEmails,
    @JsonProperty("catchall_emails") int catchallEmails,
    @JsonProperty("disposable_emails") int disposableEmails,
    @JsonProperty("credits_used") int creditsUsed,
    @JsonProperty("process_time_seconds") Double processTimeSeconds,
    @JsonProperty("result_file_path") String resultFilePath,
    @JsonProperty("download_url") String downloadUrl,
    @JsonProperty("created_at") String createdAt,
    @JsonProperty("completed_at") String completedAt
) {}
