package com.emailverify.sdk;

import com.emailverify.sdk.exception.*;
import com.emailverify.sdk.model.*;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.*;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class EmailVerifyClient implements AutoCloseable {
    private static final String DEFAULT_BASE_URL = "https://api.emailverify.ai/v1";
    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(30);
    private static final int DEFAULT_RETRIES = 3;
    private static final String USER_AGENT = "emailverify-java/1.0.0";
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    private final String apiKey;
    private final String baseUrl;
    private final int retries;
    private final OkHttpClient httpClient;
    private final ObjectMapper objectMapper;

    private EmailVerifyClient(Builder builder) {
        this.apiKey = builder.apiKey;
        this.baseUrl = builder.baseUrl != null ? builder.baseUrl.replaceAll("/$", "") : DEFAULT_BASE_URL;
        this.retries = builder.retries > 0 ? builder.retries : DEFAULT_RETRIES;

        Duration timeout = builder.timeout != null ? builder.timeout : DEFAULT_TIMEOUT;
        this.httpClient = new OkHttpClient.Builder()
            .connectTimeout(timeout.toMillis(), TimeUnit.MILLISECONDS)
            .readTimeout(timeout.toMillis(), TimeUnit.MILLISECONDS)
            .writeTimeout(timeout.toMillis(), TimeUnit.MILLISECONDS)
            .build();

        this.objectMapper = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    public static Builder builder(String apiKey) {
        return new Builder(apiKey);
    }

    private <T> T request(String method, String path, Object body, Class<T> responseClass) throws EmailVerifyException {
        return requestWithRetry(method, path, body, responseClass, 1);
    }

    private <T> T request(String method, String path, Object body, TypeReference<T> typeReference) throws EmailVerifyException {
        return requestWithRetry(method, path, body, typeReference, 1);
    }

    private <T> T requestWithRetry(String method, String path, Object body, Class<T> responseClass, int attempt) throws EmailVerifyException {
        try {
            Response response = executeRequest(method, path, body);
            return handleResponse(response, method, path, body, responseClass, null, attempt);
        } catch (IOException e) {
            throw new EmailVerifyException("Network error: " + e.getMessage(), "NETWORK_ERROR", 0);
        }
    }

    private <T> T requestWithRetry(String method, String path, Object body, TypeReference<T> typeReference, int attempt) throws EmailVerifyException {
        try {
            Response response = executeRequest(method, path, body);
            return handleResponse(response, method, path, body, null, typeReference, attempt);
        } catch (IOException e) {
            throw new EmailVerifyException("Network error: " + e.getMessage(), "NETWORK_ERROR", 0);
        }
    }

    private Response executeRequest(String method, String path, Object body) throws IOException, EmailVerifyException {
        String url = baseUrl + path;

        Request.Builder requestBuilder = new Request.Builder()
            .url(url)
            .header("EMAILVERIFY-API-KEY", apiKey)
            .header("Content-Type", "application/json")
            .header("User-Agent", USER_AGENT);

        RequestBody requestBody = null;
        if (body != null) {
            String json = objectMapper.writeValueAsString(body);
            requestBody = RequestBody.create(json, JSON);
        }

        switch (method.toUpperCase()) {
            case "GET" -> requestBuilder.get();
            case "POST" -> requestBuilder.post(requestBody != null ? requestBody : RequestBody.create("", JSON));
            case "DELETE" -> requestBuilder.delete(requestBody);
            default -> throw new IllegalArgumentException("Unsupported HTTP method: " + method);
        }

        return httpClient.newCall(requestBuilder.build()).execute();
    }

    @SuppressWarnings("unchecked")
    private <T> T handleResponse(Response response, String method, String path, Object body,
                                  Class<T> responseClass, TypeReference<T> typeReference, int attempt)
            throws EmailVerifyException, IOException {

        int statusCode = response.code();
        ResponseBody responseBody = response.body();
        String responseString = responseBody != null ? responseBody.string() : "";

        if (statusCode == 204) {
            return null;
        }

        if (statusCode >= 200 && statusCode < 300) {
            if (responseString.isEmpty()) {
                return null;
            }
            if (responseClass != null) {
                return objectMapper.readValue(responseString, responseClass);
            } else {
                return objectMapper.readValue(responseString, typeReference);
            }
        }

        return handleErrorResponse(statusCode, responseString, response, method, path, body,
                                   responseClass, typeReference, attempt);
    }

    private <T> T handleErrorResponse(int statusCode, String responseString, Response response,
                                       String method, String path, Object body,
                                       Class<T> responseClass, TypeReference<T> typeReference, int attempt)
            throws EmailVerifyException {

        String message;
        String code;
        String details = null;

        try {
            Map<String, Object> errorResponse = objectMapper.readValue(responseString, new TypeReference<>() {});
            @SuppressWarnings("unchecked")
            Map<String, Object> error = (Map<String, Object>) errorResponse.get("error");
            if (error != null) {
                message = (String) error.getOrDefault("message", response.message());
                code = (String) error.getOrDefault("code", "UNKNOWN_ERROR");
                details = (String) error.get("details");
            } else {
                message = response.message();
                code = "UNKNOWN_ERROR";
            }
        } catch (Exception e) {
            message = response.message();
            code = "UNKNOWN_ERROR";
        }

        switch (statusCode) {
            case 401 -> throw new AuthenticationException(message);
            case 403 -> {
                if ("INSUFFICIENT_CREDITS".equals(code)) {
                    throw new InsufficientCreditsException(message);
                }
                throw new EmailVerifyException(message, code, 403);
            }
            case 404 -> throw new NotFoundException(message);
            case 429 -> {
                String retryAfterHeader = response.header("Retry-After");
                int retryAfter = retryAfterHeader != null ? Integer.parseInt(retryAfterHeader) : 0;
                if (attempt < retries) {
                    int waitTime = retryAfter > 0 ? retryAfter : (1 << attempt);
                    sleep(waitTime * 1000L);
                    if (responseClass != null) {
                        return requestWithRetry(method, path, body, responseClass, attempt + 1);
                    } else {
                        return requestWithRetry(method, path, body, typeReference, attempt + 1);
                    }
                }
                throw new RateLimitException(message, retryAfter);
            }
            case 400 -> throw new ValidationException(message, details);
            case 500, 502, 503 -> {
                if (attempt < retries) {
                    sleep((1L << attempt) * 1000);
                    if (responseClass != null) {
                        return requestWithRetry(method, path, body, responseClass, attempt + 1);
                    } else {
                        return requestWithRetry(method, path, body, typeReference, attempt + 1);
                    }
                }
                throw new EmailVerifyException(message, code, statusCode);
            }
            default -> throw new EmailVerifyException(message, code, statusCode, details);
        }
    }

    private void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Verify a single email address.
     */
    public VerifyResponse verify(String email) throws EmailVerifyException {
        return verify(email, true, null);
    }

    /**
     * Verify a single email address with options.
     */
    public VerifyResponse verify(String email, boolean smtpCheck, Integer timeout) throws EmailVerifyException {
        Map<String, Object> payload = new HashMap<>();
        payload.put("email", email);
        payload.put("smtp_check", smtpCheck);
        if (timeout != null) {
            payload.put("timeout", timeout);
        }

        return request("POST", "/verify", payload, VerifyResponse.class);
    }

    /**
     * Submit a bulk verification job.
     */
    public BulkJobResponse verifyBulk(List<String> emails) throws EmailVerifyException {
        return verifyBulk(emails, true, null);
    }

    /**
     * Submit a bulk verification job with options.
     */
    public BulkJobResponse verifyBulk(List<String> emails, boolean smtpCheck, String webhookUrl) throws EmailVerifyException {
        if (emails.size() > 10000) {
            throw new ValidationException("Maximum 10,000 emails per bulk job");
        }

        Map<String, Object> payload = new HashMap<>();
        payload.put("emails", emails);
        payload.put("smtp_check", smtpCheck);
        if (webhookUrl != null) {
            payload.put("webhook_url", webhookUrl);
        }

        return request("POST", "/verify/bulk", payload, BulkJobResponse.class);
    }

    /**
     * Get the status of a bulk verification job.
     */
    public BulkJobResponse getBulkJobStatus(String jobId) throws EmailVerifyException {
        return request("GET", "/verify/bulk/" + jobId, null, BulkJobResponse.class);
    }

    /**
     * Get the results of a completed bulk verification job.
     */
    public BulkResultsResponse getBulkJobResults(String jobId) throws EmailVerifyException {
        return getBulkJobResults(jobId, 100, 0, null);
    }

    /**
     * Get the results of a completed bulk verification job with pagination.
     */
    public BulkResultsResponse getBulkJobResults(String jobId, int limit, int offset, String status) throws EmailVerifyException {
        StringBuilder path = new StringBuilder("/verify/bulk/").append(jobId).append("/results?");
        path.append("limit=").append(limit);
        path.append("&offset=").append(offset);
        if (status != null && !status.isEmpty()) {
            path.append("&status=").append(status);
        }

        return request("GET", path.toString(), null, BulkResultsResponse.class);
    }

    /**
     * Wait for bulk job completion.
     */
    public BulkJobResponse waitForBulkJobCompletion(String jobId) throws EmailVerifyException {
        return waitForBulkJobCompletion(jobId, Duration.ofSeconds(5), Duration.ofMinutes(10));
    }

    /**
     * Wait for bulk job completion with custom intervals.
     */
    public BulkJobResponse waitForBulkJobCompletion(String jobId, Duration pollInterval, Duration maxWait) throws EmailVerifyException {
        long startTime = System.currentTimeMillis();
        long maxWaitMillis = maxWait.toMillis();

        while (System.currentTimeMillis() - startTime < maxWaitMillis) {
            BulkJobResponse status = getBulkJobStatus(jobId);

            if ("completed".equals(status.status()) || "failed".equals(status.status())) {
                return status;
            }

            sleep(pollInterval.toMillis());
        }

        throw new TimeoutException("Bulk job " + jobId + " did not complete within " + maxWait.toSeconds() + " seconds");
    }

    /**
     * Get current credit balance.
     */
    public CreditsResponse getCredits() throws EmailVerifyException {
        return request("GET", "/credits", null, CreditsResponse.class);
    }

    /**
     * Create a new webhook.
     */
    public Webhook createWebhook(String url, List<String> events, String secret) throws EmailVerifyException {
        Map<String, Object> payload = new HashMap<>();
        payload.put("url", url);
        payload.put("events", events);
        if (secret != null) {
            payload.put("secret", secret);
        }

        return request("POST", "/webhooks", payload, Webhook.class);
    }

    /**
     * List all webhooks.
     */
    public List<Webhook> listWebhooks() throws EmailVerifyException {
        return request("GET", "/webhooks", null, new TypeReference<>() {});
    }

    /**
     * Delete a webhook.
     */
    public void deleteWebhook(String webhookId) throws EmailVerifyException {
        request("DELETE", "/webhooks/" + webhookId, null, Void.class);
    }

    /**
     * Verify a webhook signature.
     */
    public static boolean verifyWebhookSignature(String payload, String signature, String secret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec secretKey = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            mac.init(secretKey);
            byte[] hash = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            String expectedSignature = "sha256=" + bytesToHex(hash);
            return java.security.MessageDigest.isEqual(
                expectedSignature.getBytes(StandardCharsets.UTF_8),
                signature.getBytes(StandardCharsets.UTF_8)
            );
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            return false;
        }
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder hex = new StringBuilder();
        for (byte b : bytes) {
            hex.append(String.format("%02x", b));
        }
        return hex.toString();
    }

    @Override
    public void close() {
        httpClient.dispatcher().executorService().shutdown();
        httpClient.connectionPool().evictAll();
    }

    public static class Builder {
        private final String apiKey;
        private String baseUrl;
        private Duration timeout;
        private int retries;

        private Builder(String apiKey) {
            if (apiKey == null || apiKey.isEmpty()) {
                throw new IllegalArgumentException("API key is required");
            }
            this.apiKey = apiKey;
        }

        public Builder baseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
            return this;
        }

        public Builder timeout(Duration timeout) {
            this.timeout = timeout;
            return this;
        }

        public Builder retries(int retries) {
            this.retries = retries;
            return this;
        }

        public EmailVerifyClient build() {
            return new EmailVerifyClient(this);
        }
    }
}
