package examples;

import com.emailverify.sdk.EmailVerifyClient;
import com.emailverify.sdk.exception.*;
import com.emailverify.sdk.model.*;

import java.util.List;

/**
 * Examples demonstrating webhook management: creating, listing, deleting webhooks,
 * and verifying webhook signatures.
 */
public class WebhookExample {

    public static void main(String[] args) {
        // Get API key from environment variable
        String apiKey = System.getenv("EMAILVERIFY_API_KEY");
        if (apiKey == null || apiKey.isEmpty()) {
            System.err.println("Please set the EMAILVERIFY_API_KEY environment variable");
            System.exit(1);
        }

        try (var client = EmailVerifyClient.builder(apiKey).build()) {
            // Run examples
            createWebhookExample(client);
            listWebhooksExample(client);
            signatureVerificationExample();
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Create webhook example.
     */
    private static void createWebhookExample(EmailVerifyClient client) throws EmailVerifyException {
        System.out.println("\n=== Create Webhook Example ===");

        // Create a webhook for file completion events
        Webhook webhook = client.createWebhook(
            "https://your-app.com/webhooks/emailverify",
            List.of(WebhookEvent.FILE_COMPLETED, WebhookEvent.FILE_FAILED)
        );

        System.out.println("Webhook ID: " + webhook.id());
        System.out.println("URL: " + webhook.url());
        System.out.println("Events: " + webhook.events());
        System.out.println("Secret: " + webhook.secret());  // Store this securely!
        System.out.println("Active: " + webhook.isActive());
        System.out.println("Created At: " + webhook.createdAt());
        System.out.println("Updated At: " + webhook.updatedAt());

        System.out.println("\nIMPORTANT: Store the webhook secret securely for signature verification!");

        // Available webhook events
        System.out.println("\n--- Available Webhook Events ---");
        System.out.println("WebhookEvent.FILE_COMPLETED = \"" + WebhookEvent.FILE_COMPLETED + "\"");
        System.out.println("WebhookEvent.FILE_FAILED = \"" + WebhookEvent.FILE_FAILED + "\"");

        // Create webhook for only completed events
        System.out.println("\n--- Create Webhook for FILE_COMPLETED Only ---");
        Webhook completedOnlyWebhook = client.createWebhook(
            "https://your-app.com/webhooks/file-complete",
            List.of(WebhookEvent.FILE_COMPLETED)
        );
        System.out.println("Created webhook: " + completedOnlyWebhook.id());
        System.out.println("Events: " + completedOnlyWebhook.events());

        // Clean up - delete the test webhooks
        System.out.println("\n--- Cleaning up test webhooks ---");
        client.deleteWebhook(webhook.id());
        System.out.println("Deleted webhook: " + webhook.id());
        client.deleteWebhook(completedOnlyWebhook.id());
        System.out.println("Deleted webhook: " + completedOnlyWebhook.id());
    }

    /**
     * List webhooks example.
     */
    private static void listWebhooksExample(EmailVerifyClient client) throws EmailVerifyException {
        System.out.println("\n=== List Webhooks Example ===");

        // Create a test webhook first
        Webhook testWebhook = client.createWebhook(
            "https://test-app.com/webhook",
            List.of(WebhookEvent.FILE_COMPLETED)
        );

        // List all webhooks
        List<Webhook> webhooks = client.listWebhooks();

        System.out.println("Total webhooks: " + webhooks.size());
        System.out.println();

        for (Webhook webhook : webhooks) {
            System.out.println("--- Webhook ---");
            System.out.println("  ID: " + webhook.id());
            System.out.println("  URL: " + webhook.url());
            System.out.println("  Events: " + webhook.events());
            System.out.println("  Active: " + webhook.isActive());
            System.out.println("  Created: " + webhook.createdAt());
            System.out.println();
        }

        // Clean up
        client.deleteWebhook(testWebhook.id());
        System.out.println("Cleaned up test webhook");
    }

    /**
     * Delete webhook example.
     */
    private static void deleteWebhookExample(EmailVerifyClient client) throws EmailVerifyException {
        System.out.println("\n=== Delete Webhook Example ===");

        // Create a webhook to delete
        Webhook webhook = client.createWebhook(
            "https://temp-app.com/webhook",
            List.of(WebhookEvent.FILE_COMPLETED, WebhookEvent.FILE_FAILED)
        );
        System.out.println("Created webhook: " + webhook.id());

        // Delete the webhook
        client.deleteWebhook(webhook.id());
        System.out.println("Deleted webhook: " + webhook.id());

        // Verify deletion
        List<Webhook> webhooks = client.listWebhooks();
        boolean found = webhooks.stream()
            .anyMatch(w -> w.id().equals(webhook.id()));
        System.out.println("Webhook still exists: " + found);
    }

    /**
     * Webhook signature verification example.
     * This shows how to verify incoming webhook requests in your server.
     */
    private static void signatureVerificationExample() {
        System.out.println("\n=== Webhook Signature Verification Example ===");

        // Example webhook payload (this would come from the HTTP request body)
        String payload = """
            {
                "event": "file.completed",
                "timestamp": "2024-01-15T10:30:00Z",
                "data": {
                    "job_id": "job_abc123",
                    "status": "completed",
                    "total_emails": 1000,
                    "valid_emails": 800,
                    "invalid_emails": 150,
                    "unknown_emails": 50,
                    "download_url": "https://api.emailverify.ai/v1/verify/file/job_abc123/results"
                }
            }
            """;

        // Example signature (this would come from the X-Webhook-Signature header)
        String signature = "sha256=abc123def456...";

        // Your webhook secret (stored from when you created the webhook)
        String secret = "whsec_your_secret_here";

        // Verify the signature
        boolean isValid = EmailVerifyClient.verifyWebhookSignature(payload, signature, secret);

        if (isValid) {
            System.out.println("Signature is VALID - request is authentic");
            // Process the webhook payload safely
        } else {
            System.out.println("Signature is INVALID - reject the request!");
            // Do not process the payload
        }

        // Example: Handling webhook in a servlet/controller
        System.out.println("\n--- Example Webhook Handler (pseudo-code) ---");
        System.out.println("""
            // In your webhook endpoint handler:
            @PostMapping("/webhooks/emailverify")
            public ResponseEntity<String> handleWebhook(
                    @RequestBody String payload,
                    @RequestHeader("X-Webhook-Signature") String signature) {

                String webhookSecret = System.getenv("EMAILVERIFY_WEBHOOK_SECRET");

                boolean isValid = EmailVerifyClient.verifyWebhookSignature(
                    payload,
                    signature,
                    webhookSecret
                );

                if (!isValid) {
                    return ResponseEntity.status(401).body("Invalid signature");
                }

                // Parse the payload
                ObjectMapper mapper = new ObjectMapper();
                JsonNode event = mapper.readTree(payload);

                String eventType = event.get("event").asText();
                JsonNode data = event.get("data");

                switch (eventType) {
                    case WebhookEvent.FILE_COMPLETED:
                        String jobId = data.get("job_id").asText();
                        int validEmails = data.get("valid_emails").asInt();
                        String downloadUrl = data.get("download_url").asText();
                        // Process completed job...
                        break;

                    case WebhookEvent.FILE_FAILED:
                        String failedJobId = data.get("job_id").asText();
                        String error = data.get("error").asText();
                        // Handle failed job...
                        break;
                }

                return ResponseEntity.ok("Webhook processed");
            }
            """);
    }

    /**
     * Example showing webhook payload structure.
     */
    private static void webhookPayloadExamples() {
        System.out.println("\n=== Webhook Payload Examples ===");

        System.out.println("--- FILE_COMPLETED Event ---");
        System.out.println("""
            {
                "event": "file.completed",
                "timestamp": "2024-01-15T10:30:00Z",
                "data": {
                    "job_id": "job_abc123",
                    "status": "completed",
                    "file_name": "emails.csv",
                    "total_emails": 1000,
                    "processed_emails": 1000,
                    "valid_emails": 800,
                    "invalid_emails": 150,
                    "unknown_emails": 50,
                    "role_emails": 25,
                    "catchall_emails": 30,
                    "disposable_emails": 10,
                    "credits_used": 1000,
                    "process_time_seconds": 45.5,
                    "download_url": "https://api.emailverify.ai/v1/verify/file/job_abc123/results"
                }
            }
            """);

        System.out.println("--- FILE_FAILED Event ---");
        System.out.println("""
            {
                "event": "file.failed",
                "timestamp": "2024-01-15T10:30:00Z",
                "data": {
                    "job_id": "job_xyz789",
                    "status": "failed",
                    "file_name": "invalid.csv",
                    "error": "Invalid file format",
                    "error_details": "Could not detect email column"
                }
            }
            """);
    }
}
