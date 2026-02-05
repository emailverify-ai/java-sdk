package com.emailverify.sdk;

import com.emailverify.sdk.exception.*;
import com.emailverify.sdk.model.*;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.*;

import java.io.IOException;
import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class EmailVerifyClientTest {
    private MockWebServer mockServer;
    private EmailVerifyClient client;

    @BeforeEach
    void setUp() throws IOException {
        mockServer = new MockWebServer();
        mockServer.start();

        client = EmailVerifyClient.builder("test-api-key")
            .baseUrl(mockServer.url("/").toString())
            .timeout(Duration.ofSeconds(10))
            .retries(1)
            .build();
    }

    @AfterEach
    void tearDown() throws IOException {
        client.close();
        mockServer.shutdown();
    }

    @Test
    void builderRequiresApiKey() {
        assertThrows(IllegalArgumentException.class, () ->
            EmailVerifyClient.builder("").build()
        );
        assertThrows(IllegalArgumentException.class, () ->
            EmailVerifyClient.builder(null).build()
        );
    }

    @Test
    void builderWithDefaultOptions() {
        var client = EmailVerifyClient.builder("test-key").build();
        assertNotNull(client);
        client.close();
    }

    @Test
    void builderWithCustomOptions() {
        var client = EmailVerifyClient.builder("test-key")
            .baseUrl("https://custom.api.com")
            .timeout(Duration.ofSeconds(60))
            .retries(5)
            .build();
        assertNotNull(client);
        client.close();
    }

    @Test
    void healthCheck() throws Exception {
        String responseBody = """
            {
                "status": "ok",
                "time": 1705319400
            }
            """;

        mockServer.enqueue(new MockResponse()
            .setBody(responseBody)
            .setHeader("Content-Type", "application/json"));

        HealthResponse result = client.healthCheck();

        assertEquals("ok", result.status());
        assertEquals(1705319400, result.time());

        RecordedRequest request = mockServer.takeRequest();
        assertEquals("GET", request.getMethod());
        assertEquals("/health", request.getPath());
        // Health check should not require auth
        assertNull(request.getHeader("EV-API-KEY"));
    }

    @Test
    void verifySuccess() throws Exception {
        String responseBody = """
            {
                "email": "test@example.com",
                "status": "valid",
                "score": 0.95,
                "is_deliverable": true,
                "is_disposable": false,
                "is_catchall": false,
                "is_role": false,
                "is_free": false,
                "domain": "example.com",
                "domain_age": 10,
                "mx_records": ["mail.example.com"],
                "smtp_check": true,
                "reason": "accepted",
                "response_time": 250,
                "credits_used": 1
            }
            """;

        mockServer.enqueue(new MockResponse()
            .setBody(responseBody)
            .setHeader("Content-Type", "application/json"));

        VerifyResponse result = client.verify("test@example.com");

        assertEquals("test@example.com", result.email());
        assertEquals(Status.VALID, result.status());
        assertEquals(0.95, result.score());
        assertTrue(result.isDeliverable());
        assertFalse(result.isDisposable());
        assertEquals("example.com", result.domain());
        assertEquals(10, result.domainAge());
        assertEquals(List.of("mail.example.com"), result.mxRecords());

        RecordedRequest request = mockServer.takeRequest();
        assertEquals("POST", request.getMethod());
        assertEquals("/v1/verify/single", request.getPath());
        assertEquals("test-api-key", request.getHeader("EV-API-KEY"));
    }

    @Test
    void verifyWithOptions() throws Exception {
        String responseBody = """
            {
                "email": "test@example.com",
                "status": "valid",
                "score": 0.95,
                "is_deliverable": true,
                "is_disposable": false,
                "is_catchall": false,
                "is_role": false,
                "is_free": false,
                "credits_used": 1
            }
            """;

        mockServer.enqueue(new MockResponse()
            .setBody(responseBody)
            .setHeader("Content-Type", "application/json"));

        client.verify("test@example.com", false);

        RecordedRequest request = mockServer.takeRequest();
        String body = request.getBody().readUtf8();
        assertTrue(body.contains("\"check_smtp\":false"));
    }

    @Test
    void verifyAuthenticationError() {
        String responseBody = """
            {
                "error": {
                    "code": "INVALID_API_KEY",
                    "message": "Invalid API key"
                }
            }
            """;

        mockServer.enqueue(new MockResponse()
            .setResponseCode(401)
            .setBody(responseBody)
            .setHeader("Content-Type", "application/json"));

        assertThrows(AuthenticationException.class, () ->
            client.verify("test@example.com")
        );
    }

    @Test
    void verifyValidationError() {
        String responseBody = """
            {
                "error": {
                    "code": "INVALID_EMAIL",
                    "message": "Invalid email format"
                }
            }
            """;

        mockServer.enqueue(new MockResponse()
            .setResponseCode(400)
            .setBody(responseBody)
            .setHeader("Content-Type", "application/json"));

        assertThrows(ValidationException.class, () ->
            client.verify("invalid")
        );
    }

    @Test
    void verifyInsufficientCredits() {
        String responseBody = """
            {
                "error": {
                    "code": "INSUFFICIENT_CREDITS",
                    "message": "Not enough credits"
                }
            }
            """;

        mockServer.enqueue(new MockResponse()
            .setResponseCode(402)
            .setBody(responseBody)
            .setHeader("Content-Type", "application/json"));

        assertThrows(InsufficientCreditsException.class, () ->
            client.verify("test@example.com")
        );
    }

    @Test
    void verifyNotFound() {
        String responseBody = """
            {
                "error": {
                    "code": "NOT_FOUND",
                    "message": "Resource not found"
                }
            }
            """;

        mockServer.enqueue(new MockResponse()
            .setResponseCode(404)
            .setBody(responseBody)
            .setHeader("Content-Type", "application/json"));

        assertThrows(NotFoundException.class, () ->
            client.verify("test@example.com")
        );
    }

    @Test
    void verifyBatchSuccess() throws Exception {
        String responseBody = """
            {
                "results": [
                    {
                        "email": "user1@example.com",
                        "status": "valid",
                        "score": 0.95,
                        "is_deliverable": true,
                        "is_disposable": false,
                        "is_catchall": false,
                        "is_role": false,
                        "is_free": false,
                        "credits_used": 1
                    },
                    {
                        "email": "user2@example.com",
                        "status": "invalid",
                        "score": 0.0,
                        "is_deliverable": false,
                        "is_disposable": false,
                        "is_catchall": false,
                        "is_role": false,
                        "is_free": false,
                        "credits_used": 0
                    }
                ],
                "total_emails": 2,
                "valid_emails": 1,
                "invalid_emails": 1,
                "credits_used": 1,
                "process_time": 1500
            }
            """;

        mockServer.enqueue(new MockResponse()
            .setBody(responseBody)
            .setHeader("Content-Type", "application/json"));

        BatchVerifyResponse result = client.verifyBatch(List.of(
            "user1@example.com",
            "user2@example.com"
        ));

        assertEquals(2, result.totalEmails());
        assertEquals(1, result.validEmails());
        assertEquals(1, result.invalidEmails());
        assertEquals(2, result.results().size());
        assertEquals("user1@example.com", result.results().get(0).email());
        assertEquals(Status.VALID, result.results().get(0).status());

        RecordedRequest request = mockServer.takeRequest();
        assertEquals("POST", request.getMethod());
        assertEquals("/v1/verify/bulk", request.getPath());
        String body = request.getBody().readUtf8();
        assertTrue(body.contains("\"check_smtp\":true"));
    }

    @Test
    void verifyBatchTooManyEmails() {
        List<String> emails = java.util.Collections.nCopies(51, "test@example.com");

        assertThrows(ValidationException.class, () ->
            client.verifyBatch(emails)
        );
    }

    @Test
    void getFileJobStatus() throws Exception {
        String responseBody = """
            {
                "job_id": "job_123",
                "status": "processing",
                "file_name": "emails.csv",
                "total_emails": 100,
                "processed_emails": 50,
                "progress_percent": 50,
                "valid_emails": 40,
                "invalid_emails": 5,
                "unknown_emails": 5,
                "role_emails": 0,
                "catchall_emails": 0,
                "disposable_emails": 0,
                "credits_used": 50,
                "created_at": "2025-01-15T10:30:00Z"
            }
            """;

        mockServer.enqueue(new MockResponse()
            .setBody(responseBody)
            .setHeader("Content-Type", "application/json"));

        FileJobResponse result = client.getFileJobStatus("job_123");

        assertEquals("job_123", result.jobId());
        assertEquals(50, result.progressPercent());
        assertEquals("emails.csv", result.fileName());

        RecordedRequest request = mockServer.takeRequest();
        assertEquals("/v1/verify/file/job_123", request.getPath());
    }

    @Test
    void getFileJobStatusWithTimeout() throws Exception {
        String responseBody = """
            {
                "job_id": "job_123",
                "status": "completed",
                "file_name": "emails.csv",
                "total_emails": 100,
                "processed_emails": 100,
                "progress_percent": 100,
                "valid_emails": 90,
                "invalid_emails": 10,
                "unknown_emails": 0,
                "role_emails": 0,
                "catchall_emails": 0,
                "disposable_emails": 0,
                "credits_used": 100,
                "created_at": "2025-01-15T10:30:00Z",
                "completed_at": "2025-01-15T10:35:00Z"
            }
            """;

        mockServer.enqueue(new MockResponse()
            .setBody(responseBody)
            .setHeader("Content-Type", "application/json"));

        FileJobResponse result = client.getFileJobStatus("job_123", 60);

        assertEquals("completed", result.status());

        RecordedRequest request = mockServer.takeRequest();
        assertTrue(request.getPath().contains("timeout=60"));
    }

    @Test
    void getFileJobStatusTimeoutValidation() {
        assertThrows(ValidationException.class, () ->
            client.getFileJobStatus("job_123", 301)
        );
    }

    @Test
    void getCredits() throws Exception {
        String responseBody = """
            {
                "account_id": "abc123",
                "api_key_id": "key_xyz",
                "api_key_name": "Default API Key",
                "credits_balance": 9500,
                "credits_consumed": 500,
                "credits_added": 10000,
                "last_updated": "2025-01-15T10:30:00Z"
            }
            """;

        mockServer.enqueue(new MockResponse()
            .setBody(responseBody)
            .setHeader("Content-Type", "application/json"));

        CreditsResponse result = client.getCredits();

        assertEquals("abc123", result.accountId());
        assertEquals("key_xyz", result.apiKeyId());
        assertEquals("Default API Key", result.apiKeyName());
        assertEquals(9500, result.creditsBalance());
        assertEquals(500, result.creditsConsumed());
        assertEquals(10000, result.creditsAdded());
    }

    @Test
    void createWebhook() throws Exception {
        String responseBody = """
            {
                "id": "webhook_123",
                "url": "https://example.com/webhook",
                "events": ["file.completed", "file.failed"],
                "secret": "secret_abc123",
                "is_active": true,
                "created_at": "2025-01-15T10:30:00Z",
                "updated_at": "2025-01-15T10:30:00Z"
            }
            """;

        mockServer.enqueue(new MockResponse()
            .setBody(responseBody)
            .setHeader("Content-Type", "application/json"));

        Webhook result = client.createWebhook(
            "https://example.com/webhook",
            List.of(WebhookEvent.FILE_COMPLETED, WebhookEvent.FILE_FAILED)
        );

        assertEquals("webhook_123", result.id());
        assertEquals("https://example.com/webhook", result.url());
        assertEquals("secret_abc123", result.secret());
        assertTrue(result.isActive());
        assertEquals(List.of("file.completed", "file.failed"), result.events());

        // Verify request does not include secret
        RecordedRequest request = mockServer.takeRequest();
        String body = request.getBody().readUtf8();
        assertFalse(body.contains("\"secret\""));
    }

    @Test
    void listWebhooks() throws Exception {
        String responseBody = """
            [
                {
                    "id": "webhook_123",
                    "url": "https://example.com/webhook",
                    "events": ["file.completed"],
                    "is_active": true,
                    "created_at": "2025-01-15T10:30:00Z",
                    "updated_at": "2025-01-15T10:30:00Z"
                }
            ]
            """;

        mockServer.enqueue(new MockResponse()
            .setBody(responseBody)
            .setHeader("Content-Type", "application/json"));

        List<Webhook> result = client.listWebhooks();

        assertEquals(1, result.size());
        assertEquals("webhook_123", result.get(0).id());
        assertTrue(result.get(0).isActive());
    }

    @Test
    void deleteWebhook() throws Exception {
        mockServer.enqueue(new MockResponse().setResponseCode(204));

        assertDoesNotThrow(() -> client.deleteWebhook("webhook_123"));

        RecordedRequest request = mockServer.takeRequest();
        assertEquals("DELETE", request.getMethod());
        assertEquals("/v1/webhooks/webhook_123", request.getPath());
    }

    @Test
    void verifyWebhookSignatureValid() {
        String payload = "{\"event\":\"test\"}";
        String secret = "test-secret";
        // Pre-computed valid signature
        String signature = "sha256=ad386d9a61a0540a089d2955a07280771439f9f8c41a4b94cd404a740061c3d9";

        boolean result = EmailVerifyClient.verifyWebhookSignature(payload, signature, secret);

        assertTrue(result);
    }

    @Test
    void verifyWebhookSignatureInvalid() {
        String payload = "{\"event\":\"test\"}";
        String secret = "test-secret";
        String signature = "sha256=invalid";

        boolean result = EmailVerifyClient.verifyWebhookSignature(payload, signature, secret);

        assertFalse(result);
    }

    @Test
    void resultFiltersBuilder() {
        ResultFilters filters = ResultFilters.builder()
            .valid(true)
            .invalid(true)
            .risky(true)
            .build();

        assertTrue(filters.valid());
        assertTrue(filters.invalid());
        assertTrue(filters.risky());
        assertNull(filters.catchall());
        assertNull(filters.role());
    }

    @Test
    void getFileResultsUrlWithFilters() throws Exception {
        ResultFilters filters = ResultFilters.builder()
            .valid(true)
            .invalid(true)
            .build();

        String url = client.getFileResultsUrl("job_123", filters);

        assertTrue(url.contains("/v1/verify/file/job_123/results"));
        assertTrue(url.contains("valid=true"));
        assertTrue(url.contains("invalid=true"));
    }

    @Test
    void statusEnumFromValue() {
        assertEquals(Status.VALID, Status.fromValue("valid"));
        assertEquals(Status.INVALID, Status.fromValue("invalid"));
        assertEquals(Status.UNKNOWN, Status.fromValue("unknown"));
        assertEquals(Status.RISKY, Status.fromValue("risky"));
        assertEquals(Status.DISPOSABLE, Status.fromValue("disposable"));
        assertEquals(Status.CATCHALL, Status.fromValue("catchall"));
        assertEquals(Status.ROLE, Status.fromValue("role"));
        assertEquals(Status.UNKNOWN, Status.fromValue("unrecognized"));
    }
}

class ExceptionTest {
    @Test
    void authenticationException() {
        var error = new AuthenticationException();
        assertEquals("INVALID_API_KEY", error.getErrorCode());
        assertEquals(401, error.getStatusCode());
    }

    @Test
    void rateLimitException() {
        var error = new RateLimitException("Rate limited", 60);
        assertEquals("RATE_LIMIT_EXCEEDED", error.getErrorCode());
        assertEquals(60, error.getRetryAfter());
    }

    @Test
    void validationException() {
        var error = new ValidationException("Invalid input", "details here");
        assertEquals("INVALID_REQUEST", error.getErrorCode());
        assertEquals("details here", error.getDetails());
    }

    @Test
    void insufficientCreditsException() {
        var error = new InsufficientCreditsException();
        assertEquals("INSUFFICIENT_CREDITS", error.getErrorCode());
        assertEquals(402, error.getStatusCode());
    }

    @Test
    void notFoundException() {
        var error = new NotFoundException();
        assertEquals("NOT_FOUND", error.getErrorCode());
        assertEquals(404, error.getStatusCode());
    }

    @Test
    void timeoutException() {
        var error = new TimeoutException("Request timed out");
        assertEquals("TIMEOUT", error.getErrorCode());
        assertEquals("Request timed out", error.getMessage());
    }
}
