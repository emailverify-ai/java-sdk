package examples;

import com.emailverify.sdk.EmailVerifyClient;
import com.emailverify.sdk.exception.*;
import com.emailverify.sdk.model.*;

import java.util.List;

/**
 * Basic examples demonstrating single email verification, batch verification,
 * credits check, and health check.
 */
public class BasicExample {

    public static void main(String[] args) {
        // Get API key from environment variable
        String apiKey = System.getenv("EMAILVERIFY_API_KEY");
        if (apiKey == null || apiKey.isEmpty()) {
            System.err.println("Please set the EMAILVERIFY_API_KEY environment variable");
            System.exit(1);
        }

        try (var client = EmailVerifyClient.builder(apiKey).build()) {
            // Run examples
            healthCheckExample(client);
            singleVerificationExample(client);
            batchVerificationExample(client);
            creditsExample(client);
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Health check example - no authentication required.
     */
    private static void healthCheckExample(EmailVerifyClient client) throws EmailVerifyException {
        System.out.println("\n=== Health Check ===");

        HealthResponse health = client.healthCheck();

        System.out.println("Status: " + health.status());
        System.out.println("Time: " + health.time());
    }

    /**
     * Single email verification example.
     */
    private static void singleVerificationExample(EmailVerifyClient client) throws EmailVerifyException {
        System.out.println("\n=== Single Email Verification ===");

        // Basic verification
        VerifyResponse result = client.verify("test@gmail.com");

        System.out.println("Email: " + result.email());
        System.out.println("Status: " + result.status());
        System.out.println("Score: " + result.score());
        System.out.println("Deliverable: " + result.isDeliverable());
        System.out.println("Disposable: " + result.isDisposable());
        System.out.println("Catchall: " + result.isCatchall());
        System.out.println("Role: " + result.isRole());
        System.out.println("Free: " + result.isFree());
        System.out.println("Domain: " + result.domain());
        System.out.println("Domain Age: " + result.domainAge() + " days");
        System.out.println("MX Records: " + result.mxRecords());
        System.out.println("SMTP Check: " + result.smtpCheck());
        System.out.println("Reason: " + result.reason());
        System.out.println("Suggestion: " + result.suggestion());
        System.out.println("Credits Used: " + result.creditsUsed());

        // Check status using enum
        if (result.status() == Status.VALID) {
            System.out.println("This email is valid and deliverable!");
        } else if (result.status() == Status.INVALID) {
            System.out.println("This email is invalid.");
        } else if (result.status() == Status.DISPOSABLE) {
            System.out.println("This is a disposable email address.");
        } else if (result.status() == Status.CATCHALL) {
            System.out.println("This domain accepts all emails (catch-all).");
        } else if (result.status() == Status.ROLE) {
            System.out.println("This is a role-based email address.");
        } else if (result.status() == Status.RISKY) {
            System.out.println("This email may have deliverability issues.");
        } else {
            System.out.println("Could not determine email validity.");
        }

        // Verification with checkSmtp option
        System.out.println("\n--- Verification with checkSmtp=false ---");
        VerifyResponse quickResult = client.verify("test@example.com", false);
        System.out.println("Email: " + quickResult.email());
        System.out.println("Status: " + quickResult.status());
        System.out.println("SMTP Check: " + quickResult.smtpCheck());
    }

    /**
     * Batch verification example (synchronous, max 50 emails).
     */
    private static void batchVerificationExample(EmailVerifyClient client) throws EmailVerifyException {
        System.out.println("\n=== Batch Email Verification ===");

        List<String> emails = List.of(
            "valid@gmail.com",
            "invalid@nonexistentdomain123456.com",
            "support@example.com"
        );

        // Basic batch verification
        BatchVerifyResponse batch = client.verifyBatch(emails);

        System.out.println("Total Emails: " + batch.totalEmails());
        System.out.println("Valid Emails: " + batch.validEmails());
        System.out.println("Invalid Emails: " + batch.invalidEmails());
        System.out.println("Credits Used: " + batch.creditsUsed());
        System.out.println("Process Time: " + batch.processTime() + " ms");

        System.out.println("\n--- Individual Results ---");
        for (VerificationResult result : batch.results()) {
            System.out.printf("  %s: %s (deliverable: %b, score: %.2f)%n",
                result.email(),
                result.status(),
                result.isDeliverable(),
                result.score()
            );
        }

        // Batch verification with checkSmtp option
        System.out.println("\n--- Batch with checkSmtp=true ---");
        BatchVerifyResponse batchWithSmtp = client.verifyBatch(
            List.of("test1@gmail.com", "test2@yahoo.com"),
            true  // checkSmtp
        );

        System.out.println("Total: " + batchWithSmtp.totalEmails());
        System.out.println("Valid: " + batchWithSmtp.validEmails());

        // Count by status type
        long validCount = batchWithSmtp.results().stream()
            .filter(r -> r.status() == Status.VALID)
            .count();
        long invalidCount = batchWithSmtp.results().stream()
            .filter(r -> r.status() == Status.INVALID)
            .count();
        long disposableCount = batchWithSmtp.results().stream()
            .filter(r -> r.status() == Status.DISPOSABLE)
            .count();

        System.out.println("Valid count: " + validCount);
        System.out.println("Invalid count: " + invalidCount);
        System.out.println("Disposable count: " + disposableCount);
    }

    /**
     * Credits example.
     */
    private static void creditsExample(EmailVerifyClient client) throws EmailVerifyException {
        System.out.println("\n=== Credits Information ===");

        CreditsResponse credits = client.getCredits();

        System.out.println("Account ID: " + credits.accountId());
        System.out.println("API Key ID: " + credits.apiKeyId());
        System.out.println("API Key Name: " + credits.apiKeyName());
        System.out.println("Credits Balance: " + credits.creditsBalance());
        System.out.println("Credits Consumed: " + credits.creditsConsumed());
        System.out.println("Credits Added: " + credits.creditsAdded());
        System.out.println("Last Updated: " + credits.lastUpdated());

        // Check if credits are low
        if (credits.creditsBalance() < 100) {
            System.out.println("WARNING: Credits are running low!");
        }
    }

    /**
     * Error handling example.
     */
    private static void errorHandlingExample(EmailVerifyClient client) {
        System.out.println("\n=== Error Handling Example ===");

        try {
            VerifyResponse result = client.verify("test@example.com");
            System.out.println("Status: " + result.status());
        } catch (AuthenticationException e) {
            System.err.println("Authentication failed: Invalid API key");
        } catch (RateLimitException e) {
            System.err.printf("Rate limited: Retry after %d seconds%n", e.getRetryAfter());
        } catch (ValidationException e) {
            System.err.printf("Validation error: %s%n", e.getMessage());
            if (e.getDetails() != null) {
                System.err.printf("Details: %s%n", e.getDetails());
            }
        } catch (InsufficientCreditsException e) {
            System.err.println("Insufficient credits: " + e.getMessage());
        } catch (NotFoundException e) {
            System.err.println("Resource not found: " + e.getMessage());
        } catch (TimeoutException e) {
            System.err.println("Request timed out: " + e.getMessage());
        } catch (EmailVerifyException e) {
            System.err.printf("API error [%s]: %s%n", e.getErrorCode(), e.getMessage());
        }
    }
}
