package com.emailverify.sdk.model;

/**
 * Available webhook events.
 */
public final class WebhookEvent {
    public static final String FILE_COMPLETED = "file.completed";
    public static final String FILE_FAILED = "file.failed";

    private WebhookEvent() {
        // Prevent instantiation
    }
}
