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
    void verifySuccess() throws Exception {
        String responseBody = """
            {
                "email": "test@example.com",
                "status": "valid",
                "result": {
                    "deliverable": true,
                    "valid_format": true,
                    "valid_domain": true,
                    "valid_mx": true,
                    "disposable": false,
                    "role": false,
                    "catchall": false,
                    "free": false,
                    "smtp_valid": true
                },
                "score": 0.95,
                "reason": null,
                "credits_used": 1
            }
            """;

        mockServer.enqueue(new MockResponse()
            .setBody(responseBody)
            .setHeader("Content-Type", "application/json"));

        VerifyResponse result = client.verify("test@example.com");

        assertEquals("test@example.com", result.email());
        assertEquals("valid", result.status());
        assertEquals(0.95, result.score());
        assertTrue(result.result().deliverable());
        assertFalse(result.result().disposable());

        RecordedRequest request = mockServer.takeRequest();
        assertEquals("POST", request.getMethod());
        assertEquals("/verify", request.getPath());
        assertEquals("test-api-key", request.getHeader("EMAILVERIFY-API-KEY"));
    }

    @Test
    void verifyWithOptions() throws Exception {
        String responseBody = """
            {
                "email": "test@example.com",
                "status": "valid",
                "result": {},
                "score": 0.95,
                "credits_used": 1
            }
            """;

        mockServer.enqueue(new MockResponse()
            .setBody(responseBody)
            .setHeader("Content-Type", "application/json"));

        client.verify("test@example.com", false, 5000);

        RecordedRequest request = mockServer.takeRequest();
        String body = request.getBody().readUtf8();
        assertTrue(body.contains("\"smtp_check\":false"));
        assertTrue(body.contains("\"timeout\":5000"));
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
            .setResponseCode(403)
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
    void verifyBulkSuccess() throws Exception {
        String responseBody = """
            {
                "job_id": "job_123",
                "status": "processing",
                "total": 3,
                "processed": 0,
                "valid": 0,
                "invalid": 0,
                "unknown": 0,
                "credits_used": 3,
                "created_at": "2025-01-15T10:30:00Z"
            }
            """;

        mockServer.enqueue(new MockResponse()
            .setBody(responseBody)
            .setHeader("Content-Type", "application/json"));

        BulkJobResponse result = client.verifyBulk(List.of(
            "user1@example.com",
            "user2@example.com",
            "user3@example.com"
        ));

        assertEquals("job_123", result.jobId());
        assertEquals("processing", result.status());
        assertEquals(3, result.total());
    }

    @Test
    void verifyBulkTooManyEmails() {
        List<String> emails = java.util.Collections.nCopies(10001, "test@example.com");

        assertThrows(ValidationException.class, () ->
            client.verifyBulk(emails)
        );
    }

    @Test
    void getBulkJobStatus() throws Exception {
        String responseBody = """
            {
                "job_id": "job_123",
                "status": "processing",
                "total": 100,
                "processed": 50,
                "valid": 40,
                "invalid": 5,
                "unknown": 5,
                "credits_used": 100,
                "created_at": "2025-01-15T10:30:00Z",
                "progress_percent": 50
            }
            """;

        mockServer.enqueue(new MockResponse()
            .setBody(responseBody)
            .setHeader("Content-Type", "application/json"));

        BulkJobResponse result = client.getBulkJobStatus("job_123");

        assertEquals("job_123", result.jobId());
        assertEquals(50, result.progressPercent());

        RecordedRequest request = mockServer.takeRequest();
        assertEquals("/verify/bulk/job_123", request.getPath());
    }

    @Test
    void getBulkJobResults() throws Exception {
        String responseBody = """
            {
                "job_id": "job_123",
                "total": 100,
                "limit": 50,
                "offset": 0,
                "results": [
                    {
                        "email": "test@example.com",
                        "status": "valid",
                        "result": {"deliverable": true},
                        "score": 0.95
                    }
                ]
            }
            """;

        mockServer.enqueue(new MockResponse()
            .setBody(responseBody)
            .setHeader("Content-Type", "application/json"));

        BulkResultsResponse result = client.getBulkJobResults("job_123", 50, 0, "valid");

        assertEquals("job_123", result.jobId());
        assertEquals(1, result.results().size());
        assertEquals("test@example.com", result.results().get(0).email());

        RecordedRequest request = mockServer.takeRequest();
        assertTrue(request.getPath().contains("limit=50"));
        assertTrue(request.getPath().contains("offset=0"));
        assertTrue(request.getPath().contains("status=valid"));
    }

    @Test
    void getCredits() throws Exception {
        String responseBody = """
            {
                "available": 9500,
                "used": 500,
                "total": 10000,
                "plan": "Professional",
                "resets_at": "2025-02-01T00:00:00Z",
                "rate_limit": {
                    "requests_per_hour": 10000,
                    "remaining": 9850
                }
            }
            """;

        mockServer.enqueue(new MockResponse()
            .setBody(responseBody)
            .setHeader("Content-Type", "application/json"));

        CreditsResponse result = client.getCredits();

        assertEquals(9500, result.available());
        assertEquals("Professional", result.plan());
        assertEquals(9850, result.rateLimit().remaining());
    }

    @Test
    void createWebhook() throws Exception {
        String responseBody = """
            {
                "id": "webhook_123",
                "url": "https://example.com/webhook",
                "events": ["verification.completed"],
                "created_at": "2025-01-15T10:30:00Z"
            }
            """;

        mockServer.enqueue(new MockResponse()
            .setBody(responseBody)
            .setHeader("Content-Type", "application/json"));

        Webhook result = client.createWebhook(
            "https://example.com/webhook",
            List.of("verification.completed"),
            "secret"
        );

        assertEquals("webhook_123", result.id());
        assertEquals("https://example.com/webhook", result.url());
    }

    @Test
    void listWebhooks() throws Exception {
        String responseBody = """
            [
                {
                    "id": "webhook_123",
                    "url": "https://example.com/webhook",
                    "events": ["verification.completed"],
                    "created_at": "2025-01-15T10:30:00Z"
                }
            ]
            """;

        mockServer.enqueue(new MockResponse()
            .setBody(responseBody)
            .setHeader("Content-Type", "application/json"));

        List<Webhook> result = client.listWebhooks();

        assertEquals(1, result.size());
        assertEquals("webhook_123", result.get(0).id());
    }

    @Test
    void deleteWebhook() throws Exception {
        mockServer.enqueue(new MockResponse().setResponseCode(204));

        assertDoesNotThrow(() -> client.deleteWebhook("webhook_123"));

        RecordedRequest request = mockServer.takeRequest();
        assertEquals("DELETE", request.getMethod());
        assertEquals("/webhooks/webhook_123", request.getPath());
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
        assertEquals(403, error.getStatusCode());
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
