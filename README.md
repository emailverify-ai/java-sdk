# EmailVerify Java SDK

Official EmailVerify Java SDK for email verification.

**Documentation:** https://emailverify.ai/docs

## Requirements

- Java 17 or higher
- Maven or Gradle

## Installation

### Maven

```xml
<repositories>
    <repository>
        <id>jitpack.io</id>
        <url>https://jitpack.io</url>
    </repository>
</repositories>

<dependency>
    <groupId>com.github.emailverify-ai</groupId>
    <artifactId>java-sdk</artifactId>
    <version>v1.0.0</version>
</dependency>
```

### Gradle

```groovy
repositories {
    maven { url 'https://jitpack.io' }
}

dependencies {
    implementation 'com.github.emailverify-ai:java-sdk:v1.0.0'
}
```

## Quick Start

```java
import com.emailverify.sdk.EmailVerifyClient;
import com.emailverify.sdk.model.VerifyResponse;
import com.emailverify.sdk.model.Status;

public class Main {
    public static void main(String[] args) throws Exception {
        try (var client = EmailVerifyClient.builder("your-api-key").build()) {
            VerifyResponse result = client.verify("user@example.com");
            System.out.println(result.status());        // Status.VALID, Status.INVALID, etc.
            System.out.println(result.isDeliverable()); // true or false
        }
    }
}
```

## Configuration

```java
var client = EmailVerifyClient.builder("your-api-key")
    .baseUrl("https://api.emailverify.ai")       // Optional
    .timeout(Duration.ofSeconds(30))             // Optional (default: 30s)
    .retries(3)                                  // Optional (default: 3)
    .build();
```

## Health Check

```java
import com.emailverify.sdk.model.HealthResponse;

// No authentication required
HealthResponse health = client.healthCheck();
System.out.println(health.status());  // "ok"
System.out.println(health.time());    // timestamp
```

## Single Email Verification

Uses the `/verify/single` endpoint.

```java
import com.emailverify.sdk.model.VerifyResponse;
import com.emailverify.sdk.model.Status;

// Basic verification
VerifyResponse result = client.verify("user@example.com");

// With checkSmtp option
VerifyResponse result = client.verify("user@example.com", true);

// Access response fields (flat structure)
System.out.println(result.email());           // "user@example.com"
System.out.println(result.status());          // Status.VALID
System.out.println(result.score());           // 0.95
System.out.println(result.isDeliverable());   // true
System.out.println(result.isDisposable());    // false
System.out.println(result.isCatchall());      // false
System.out.println(result.isRole());          // false
System.out.println(result.isFree());          // true
System.out.println(result.domain());          // "example.com"
System.out.println(result.domainAge());       // 365
System.out.println(result.mxRecords());       // ["mx1.example.com", "mx2.example.com"]
System.out.println(result.smtpCheck());       // true
System.out.println(result.reason());          // "Valid mailbox"
System.out.println(result.suggestion());      // null
System.out.println(result.creditsUsed());     // 1
```

## Batch Email Verification (Synchronous)

Synchronous batch verification with maximum 50 emails per request.

```java
import com.emailverify.sdk.model.BatchVerifyResponse;
import com.emailverify.sdk.model.VerificationResult;
import com.emailverify.sdk.model.Status;

// Verify batch (max 50 emails)
BatchVerifyResponse batch = client.verifyBatch(
    List.of("user1@example.com", "user2@example.com", "user3@example.com")
);

// With checkSmtp option
BatchVerifyResponse batch = client.verifyBatch(
    List.of("user1@example.com", "user2@example.com"),
    true  // checkSmtp
);

// Access batch results
System.out.println(batch.totalEmails());    // 3
System.out.println(batch.validEmails());    // 2
System.out.println(batch.invalidEmails());  // 1
System.out.println(batch.creditsUsed());    // 3
System.out.println(batch.processTime());    // 1234 (milliseconds)

// Iterate through results
for (VerificationResult result : batch.results()) {
    System.out.printf("%s: %s (deliverable: %b)%n",
        result.email(),
        result.status(),        // Status.VALID, Status.INVALID, etc.
        result.isDeliverable()
    );
}
```

## File Upload (Asynchronous)

For large email lists, use file upload for asynchronous processing.

```java
import com.emailverify.sdk.model.FileUploadResponse;
import com.emailverify.sdk.model.FileJobResponse;
import com.emailverify.sdk.model.ResultFilters;
import java.io.File;
import java.time.Duration;

// Upload file for verification
File file = new File("emails.csv");
FileUploadResponse upload = client.uploadFile(file);

// With options
FileUploadResponse upload = client.uploadFile(
    file,
    true,           // checkSmtp
    "email",        // emailColumn (auto-detected if null)
    true            // preserveOriginal columns
);

System.out.println(upload.taskId());        // "task_abc123"
System.out.println(upload.status());        // "queued"
System.out.println(upload.uniqueEmails());  // 1000
System.out.println(upload.totalRows());     // 1050

// Get job status
FileJobResponse status = client.getFileJobStatus(upload.taskId());

// Get job status with long-polling (waits up to timeout seconds)
FileJobResponse status = client.getFileJobStatus(upload.taskId(), 60);

System.out.println(status.status());           // "processing" or "completed"
System.out.println(status.progressPercent());  // 45
System.out.println(status.processedEmails());  // 450
System.out.println(status.totalEmails());      // 1000

// Wait for completion (uses long-polling internally)
FileJobResponse completed = client.waitForFileJobCompletion(upload.taskId());

// With custom max wait time
FileJobResponse completed = client.waitForFileJobCompletion(
    upload.taskId(),
    Duration.ofMinutes(30)
);

System.out.println(completed.status());        // "completed"
System.out.println(completed.validEmails());   // 800
System.out.println(completed.invalidEmails()); // 150
System.out.println(completed.unknownEmails()); // 50
System.out.println(completed.downloadUrl());   // URL to download results
```

## Downloading Results

Download verification results with optional filtering.

```java
import com.emailverify.sdk.model.ResultFilters;

// Get download URL
String url = client.getFileResultsUrl(jobId);

// Get download URL with filters (only valid and risky emails)
ResultFilters filters = ResultFilters.builder()
    .valid(true)
    .risky(true)
    .build();
String url = client.getFileResultsUrl(jobId, filters);

// Download results as byte array
byte[] data = client.downloadFileResults(jobId);

// Download with filters
byte[] data = client.downloadFileResults(jobId, filters);

// Save to file
Files.write(Path.of("results.csv"), data);
```

## Credits

```java
import com.emailverify.sdk.model.CreditsResponse;

CreditsResponse credits = client.getCredits();

System.out.println(credits.creditsBalance());   // 9500
System.out.println(credits.creditsConsumed());  // 500
System.out.println(credits.creditsAdded());     // 10000
System.out.println(credits.accountId());        // "acc_123"
System.out.println(credits.apiKeyId());         // "key_456"
System.out.println(credits.apiKeyName());       // "Production Key"
System.out.println(credits.lastUpdated());      // "2024-01-15T10:30:00Z"
```

## Webhooks

```java
import com.emailverify.sdk.model.Webhook;
import com.emailverify.sdk.model.WebhookEvent;

// Create a webhook
Webhook webhook = client.createWebhook(
    "https://your-app.com/webhooks/emailverify",
    List.of(WebhookEvent.FILE_COMPLETED, WebhookEvent.FILE_FAILED)
);

System.out.println(webhook.id());        // "wh_abc123"
System.out.println(webhook.url());       // "https://your-app.com/webhooks/emailverify"
System.out.println(webhook.events());    // ["file.completed", "file.failed"]
System.out.println(webhook.secret());    // "whsec_xxx" - Store this securely!
System.out.println(webhook.isActive());  // true

// List webhooks
List<Webhook> webhooks = client.listWebhooks();

// Delete a webhook
client.deleteWebhook(webhook.id());

// Verify webhook signature
boolean isValid = EmailVerifyClient.verifyWebhookSignature(
    rawBody,          // Raw request body
    signatureHeader,  // X-Webhook-Signature header
    webhookSecret     // Secret from webhook creation
);
```

### Webhook Events

| Event | Description |
|-------|-------------|
| `WebhookEvent.FILE_COMPLETED` | File verification job completed successfully |
| `WebhookEvent.FILE_FAILED` | File verification job failed |

## Verification Status Values

| Status | Description |
|--------|-------------|
| `Status.VALID` | Email exists and can receive messages |
| `Status.INVALID` | Email does not exist or cannot receive messages |
| `Status.UNKNOWN` | Could not determine validity with certainty |
| `Status.RISKY` | Email may have deliverability issues |
| `Status.DISPOSABLE` | Temporary/disposable email address |
| `Status.CATCHALL` | Domain accepts all emails (catch-all) |
| `Status.ROLE` | Role-based email address (e.g., info@, support@) |

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

## Examples

See the [examples](./examples) directory for complete working examples:

- [BasicExample.java](./examples/BasicExample.java) - Single and batch verification
- [FileUploadExample.java](./examples/FileUploadExample.java) - File upload and async processing
- [WebhookExample.java](./examples/WebhookExample.java) - Webhook management

## License

MIT
