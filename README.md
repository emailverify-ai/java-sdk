# EmailVerify Java SDK

Official EmailVerify Java SDK for email verification.

**Documentation:** https://emailverify.ai/docs

## Requirements

- Java 17 or higher
- Maven or Gradle

## Installation

### Maven

```xml
<dependency>
    <groupId>com.emailverify</groupId>
    <artifactId>emailverify-java</artifactId>
    <version>1.0.0</version>
</dependency>
```

### Gradle

```groovy
implementation 'com.emailverify:emailverify-java:1.0.0'
```

## Quick Start

```java
import com.emailverify.sdk.EmailVerifyClient;
import com.emailverify.sdk.model.VerifyResponse;

public class Main {
    public static void main(String[] args) throws Exception {
        try (var client = EmailVerifyClient.builder("your-api-key").build()) {
            VerifyResponse result = client.verify("user@example.com");
            System.out.println(result.status()); // "valid", "invalid", "unknown", or "accept_all"
        }
    }
}
```

## Configuration

```java
var client = EmailVerifyClient.builder("your-api-key")
    .baseUrl("https://api.emailverify.ai/v1")  // Optional
    .timeout(Duration.ofSeconds(30))               // Optional (default: 30s)
    .retries(3)                                    // Optional (default: 3)
    .build();
```

## Single Email Verification

```java
// Basic verification
VerifyResponse result = client.verify("user@example.com");

// With options
VerifyResponse result = client.verify(
    "user@example.com",
    true,   // smtpCheck
    5000    // timeout in milliseconds
);

System.out.println(result.email());                    // "user@example.com"
System.out.println(result.status());                   // "valid"
System.out.println(result.score());                    // 0.95
System.out.println(result.result().deliverable());     // true
System.out.println(result.result().disposable());      // false
```

## Bulk Email Verification

```java
import com.emailverify.sdk.model.BulkJobResponse;
import com.emailverify.sdk.model.BulkResultsResponse;

// Submit a bulk verification job
BulkJobResponse job = client.verifyBulk(
    List.of("user1@example.com", "user2@example.com", "user3@example.com"),
    true,  // smtpCheck
    "https://your-app.com/webhooks/emailverify"  // webhookUrl (optional)
);

System.out.println(job.jobId());  // "job_abc123xyz"

// Check job status
BulkJobResponse status = client.getBulkJobStatus(job.jobId());
System.out.println(status.progressPercent());  // 45

// Wait for completion (polling)
BulkJobResponse completed = client.waitForBulkJobCompletion(
    job.jobId(),
    Duration.ofSeconds(5),   // poll interval
    Duration.ofMinutes(10)   // max wait
);

// Get results
BulkResultsResponse results = client.getBulkJobResults(
    job.jobId(),
    100,      // limit
    0,        // offset
    "valid"   // status filter (optional)
);

for (var item : results.results()) {
    System.out.printf("%s: %s%n", item.email(), item.status());
}
```

## Credits

```java
import com.emailverify.sdk.model.CreditsResponse;

CreditsResponse credits = client.getCredits();

System.out.println(credits.available());                    // 9500
System.out.println(credits.plan());                         // "Professional"
System.out.println(credits.rateLimit().remaining());        // 9850
```

## Webhooks

```java
import com.emailverify.sdk.model.Webhook;

// Create a webhook
Webhook webhook = client.createWebhook(
    "https://your-app.com/webhooks/emailverify",
    List.of("verification.completed", "bulk.completed"),
    "your-webhook-secret"
);

// List webhooks
List<Webhook> webhooks = client.listWebhooks();

// Delete a webhook
client.deleteWebhook(webhook.id());

// Verify webhook signature
boolean isValid = EmailVerifyClient.verifyWebhookSignature(
    rawBody,
    signatureHeader,
    "your-webhook-secret"
);
```

## Error Handling

```java
import com.emailverify.sdk.exception.*;

try {
    VerifyResponse result = client.verify("user@example.com");
} catch (AuthenticationException e) {
    System.out.println("Invalid API key");
} catch (RateLimitException e) {
    System.out.printf("Rate limited. Retry after %d seconds%n", e.getRetryAfter());
} catch (ValidationException e) {
    System.out.printf("Invalid input: %s%n", e.getMessage());
    System.out.printf("Details: %s%n", e.getDetails());
} catch (InsufficientCreditsException e) {
    System.out.println("Not enough credits");
} catch (NotFoundException e) {
    System.out.println("Resource not found");
} catch (TimeoutException e) {
    System.out.println("Request timed out");
} catch (EmailVerifyException e) {
    System.out.printf("Error [%s]: %s%n", e.getErrorCode(), e.getMessage());
}
```

## AutoCloseable Support

The client implements `AutoCloseable` for proper resource management:

```java
try (var client = EmailVerifyClient.builder("your-api-key").build()) {
    VerifyResponse result = client.verify("user@example.com");
    System.out.println(result.status());
}
// Connection is automatically closed
```

## Verification Status Values

- `valid` - Email exists and can receive messages
- `invalid` - Email doesn't exist or can't receive messages
- `unknown` - Could not determine validity with certainty
- `accept_all` - Domain accepts all emails (catch-all)

## Webhook Events

Available webhook events:
- `verification.completed` - Single email verification completed
- `bulk.completed` - Bulk job finished
- `bulk.failed` - Bulk job failed
- `credits.low` - Credits below threshold

## License

MIT
