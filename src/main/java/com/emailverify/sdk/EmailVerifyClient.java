package com.emailverify.sdk;

import com.emailverify.sdk.exception.*;
import com.emailverify.sdk.model.*;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.*;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.File;
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
    private static final String DEFAULT_BASE_URL = "https://api.emailverify.ai";
    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(30);
    private static final int DEFAULT_RETRIES = 3;
    private static final String USER_AGENT = "emailverify-java/1.0.0";
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");
    private static final int MAX_BATCH_EMAILS = 50;

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
        return requestWithRetry(method, path, body, responseClass, 1, true);
    }

    private <T> T request(String method, String path, Object body, TypeReference<T> typeReference) throws EmailVerifyException {
        return requestWithRetry(method, path, body, typeReference, 1, true);
    }

    private <T> T requestNoAuth(String method, String path, Object body, Class<T> responseClass) throws EmailVerifyException {
        return requestWithRetry(method, path, body, responseClass, 1, false);
    }

    private <T> T requestWithRetry(String method, String path, Object body, Class<T> responseClass, int attempt, boolean requireAuth) throws EmailVerifyException {
        try {
            Response response = executeRequest(method, path, body, requireAuth);
            return handleResponse(response, method, path, body, responseClass, null, attempt, requireAuth);
        } catch (IOException e) {
            throw new EmailVerifyException("Network error: " + e.getMessage(), "NETWORK_ERROR", 0);
        }
    }

    private <T> T requestWithRetry(String method, String path, Object body, TypeReference<T> typeReference, int attempt, boolean requireAuth) throws EmailVerifyException {
        try {
            Response response = executeRequest(method, path, body, requireAuth);
            return handleResponse(response, method, path, body, null, typeReference, attempt, requireAuth);
        } catch (IOException e) {
            throw new EmailVerifyException("Network error: " + e.getMessage(), "NETWORK_ERROR", 0);
        }
    }

    private Response executeRequest(String method, String path, Object body, boolean requireAuth) throws IOException, EmailVerifyException {
        String url = baseUrl + path;

        Request.Builder requestBuilder = new Request.Builder()
            .url(url)
            .header("Content-Type", "application/json")
            .header("User-Agent", USER_AGENT);

        if (requireAuth) {
            requestBuilder.header("EV-API-KEY", apiKey);
        }

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

    private Response executeMultipartRequest(String path, File file, Map<String, String> fields) throws IOException {
        String url = baseUrl + path;

        MultipartBody.Builder multipartBuilder = new MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("file", file.getName(),
                RequestBody.create(file, MediaType.parse("application/octet-stream")));

        for (Map.Entry<String, String> entry : fields.entrySet()) {
            multipartBuilder.addFormDataPart(entry.getKey(), entry.getValue());
        }

        Request request = new Request.Builder()
            .url(url)
            .header("EV-API-KEY", apiKey)
            .header("User-Agent", USER_AGENT)
            .post(multipartBuilder.build())
            .build();

        return httpClient.newCall(request).execute();
    }

    @SuppressWarnings("unchecked")
    private <T> T handleResponse(Response response, String method, String path, Object body,
                                  Class<T> responseClass, TypeReference<T> typeReference, int attempt, boolean requireAuth)
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
            // Try to parse as ApiResponse wrapper {success, code, message, data}
            // Some endpoints (like /health) return data directly without wrapper
            try {
                ApiResponse apiResponse = objectMapper.readValue(responseString, ApiResponse.class);
                if (apiResponse.data() != null) {
                    // Has wrapper, extract data field
                    if (responseClass != null) {
                        return objectMapper.treeToValue(apiResponse.data(), responseClass);
                    } else {
                        return objectMapper.readValue(objectMapper.treeAsTokens(apiResponse.data()), typeReference);
                    }
                }
            } catch (Exception e) {
                // Not a wrapper response, parse directly
            }
            // Parse directly without wrapper
            if (responseClass != null) {
                return objectMapper.readValue(responseString, responseClass);
            } else {
                return objectMapper.readValue(responseString, typeReference);
            }
        }

        return handleErrorResponse(statusCode, responseString, response, method, path, body,
                                   responseClass, typeReference, attempt, requireAuth);
    }

    private <T> T handleErrorResponse(int statusCode, String responseString, Response response,
                                       String method, String path, Object body,
                                       Class<T> responseClass, TypeReference<T> typeReference, int attempt, boolean requireAuth)
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
                message = (String) errorResponse.getOrDefault("message", response.message());
                code = "UNKNOWN_ERROR";
            }
        } catch (Exception e) {
            message = response.message();
            code = "UNKNOWN_ERROR";
        }

        switch (statusCode) {
            case 401 -> throw new AuthenticationException(message);
            case 402 -> throw new InsufficientCreditsException(message);
            case 404 -> throw new NotFoundException(message);
            case 429 -> {
                String retryAfterHeader = response.header("Retry-After");
                int retryAfter = retryAfterHeader != null ? Integer.parseInt(retryAfterHeader) : 0;
                if (attempt < retries) {
                    int waitTime = retryAfter > 0 ? retryAfter : (1 << attempt);
                    sleep(waitTime * 1000L);
                    if (responseClass != null) {
                        return requestWithRetry(method, path, body, responseClass, attempt + 1, requireAuth);
                    } else {
                        return requestWithRetry(method, path, body, typeReference, attempt + 1, requireAuth);
                    }
                }
                throw new RateLimitException(message, retryAfter);
            }
            case 400 -> throw new ValidationException(message, details);
            case 500, 502, 503 -> {
                if (attempt < retries) {
                    sleep((1L << attempt) * 1000);
                    if (responseClass != null) {
                        return requestWithRetry(method, path, body, responseClass, attempt + 1, requireAuth);
                    } else {
                        return requestWithRetry(method, path, body, typeReference, attempt + 1, requireAuth);
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

    // ==================== Health Check ====================

    /**
     * Perform a health check. No authentication required.
     */
    public HealthResponse healthCheck() throws EmailVerifyException {
        return requestNoAuth("GET", "/health", null, HealthResponse.class);
    }

    // ==================== Single Verification ====================

    /**
     * Verify a single email address.
     */
    public VerifyResponse verify(String email) throws EmailVerifyException {
        return verify(email, true);
    }

    /**
     * Verify a single email address with options.
     */
    public VerifyResponse verify(String email, boolean checkSmtp) throws EmailVerifyException {
        Map<String, Object> payload = new HashMap<>();
        payload.put("email", email);
        payload.put("check_smtp", checkSmtp);

        return request("POST", "/v1/verify/single", payload, VerifyResponse.class);
    }

    // ==================== Batch Verification (Synchronous) ====================

    /**
     * Verify multiple email addresses synchronously.
     * Maximum 50 emails per request.
     */
    public BatchVerifyResponse verifyBatch(List<String> emails) throws EmailVerifyException {
        return verifyBatch(emails, true);
    }

    /**
     * Verify multiple email addresses synchronously with options.
     * Maximum 50 emails per request.
     */
    public BatchVerifyResponse verifyBatch(List<String> emails, boolean checkSmtp) throws EmailVerifyException {
        if (emails.size() > MAX_BATCH_EMAILS) {
            throw new ValidationException("Maximum " + MAX_BATCH_EMAILS + " emails per batch request. For larger lists, use file upload.");
        }

        Map<String, Object> payload = new HashMap<>();
        payload.put("emails", emails);
        payload.put("check_smtp", checkSmtp);

        return request("POST", "/v1/verify/bulk", payload, BatchVerifyResponse.class);
    }

    // ==================== File Upload ====================

    /**
     * Upload a file for verification.
     */
    public FileUploadResponse uploadFile(File file) throws EmailVerifyException {
        return uploadFile(file, true, null, true);
    }

    /**
     * Upload a file for verification with options.
     *
     * @param file            The file to upload (CSV, Excel, or TXT)
     * @param checkSmtp       Whether to perform SMTP verification
     * @param emailColumn     Column name containing email addresses (auto-detected if null)
     * @param preserveOriginal Keep original columns in result file
     */
    public FileUploadResponse uploadFile(File file, boolean checkSmtp, String emailColumn, boolean preserveOriginal) throws EmailVerifyException {
        Map<String, String> fields = new HashMap<>();
        fields.put("check_smtp", String.valueOf(checkSmtp));
        fields.put("preserve_original", String.valueOf(preserveOriginal));
        if (emailColumn != null) {
            fields.put("email_column", emailColumn);
        }

        try {
            Response response = executeMultipartRequest("/v1/verify/file", file, fields);
            int statusCode = response.code();
            ResponseBody responseBody = response.body();
            String responseString = responseBody != null ? responseBody.string() : "";

            if (statusCode >= 200 && statusCode < 300) {
                return objectMapper.readValue(responseString, FileUploadResponse.class);
            }

            return handleErrorResponse(statusCode, responseString, response, "POST", "/v1/verify/file", null,
                FileUploadResponse.class, null, 1, true);
        } catch (IOException e) {
            throw new EmailVerifyException("Network error: " + e.getMessage(), "NETWORK_ERROR", 0);
        }
    }

    // ==================== File Job Status ====================

    /**
     * Get the status of a file verification job.
     */
    public FileJobResponse getFileJobStatus(String jobId) throws EmailVerifyException {
        return getFileJobStatus(jobId, null);
    }

    /**
     * Get the status of a file verification job with long-polling support.
     *
     * @param jobId   The job ID
     * @param timeout Long-polling timeout in seconds (0-300). If set, request waits until job completes or timeout.
     */
    public FileJobResponse getFileJobStatus(String jobId, Integer timeout) throws EmailVerifyException {
        StringBuilder path = new StringBuilder("/v1/verify/file/").append(jobId);
        if (timeout != null && timeout > 0) {
            if (timeout > 300) {
                throw new ValidationException("Timeout must be between 0 and 300 seconds");
            }
            path.append("?timeout=").append(timeout);
        }

        return request("GET", path.toString(), null, FileJobResponse.class);
    }

    /**
     * Wait for file job completion using long-polling.
     */
    public FileJobResponse waitForFileJobCompletion(String jobId) throws EmailVerifyException {
        return waitForFileJobCompletion(jobId, Duration.ofMinutes(10));
    }

    /**
     * Wait for file job completion with custom max wait time.
     * Uses long-polling with 60-second intervals.
     */
    public FileJobResponse waitForFileJobCompletion(String jobId, Duration maxWait) throws EmailVerifyException {
        long startTime = System.currentTimeMillis();
        long maxWaitMillis = maxWait.toMillis();

        while (System.currentTimeMillis() - startTime < maxWaitMillis) {
            // Use 60 second long-polling timeout
            FileJobResponse status = getFileJobStatus(jobId, 60);

            if ("completed".equals(status.status()) || "failed".equals(status.status())) {
                return status;
            }
        }

        throw new TimeoutException("File job " + jobId + " did not complete within " + maxWait.toSeconds() + " seconds");
    }

    // ==================== Results Download ====================

    /**
     * Get the download URL for file verification results.
     * Returns the URL to download the results file.
     */
    public String getFileResultsUrl(String jobId) throws EmailVerifyException {
        return getFileResultsUrl(jobId, null);
    }

    /**
     * Get the download URL for file verification results with filters.
     *
     * @param jobId   The job ID
     * @param filters Optional filters to include only specific result types
     */
    public String getFileResultsUrl(String jobId, ResultFilters filters) throws EmailVerifyException {
        StringBuilder path = new StringBuilder("/v1/verify/file/").append(jobId).append("/results");

        if (filters != null) {
            StringBuilder queryParams = new StringBuilder();
            if (filters.valid() != null && filters.valid()) {
                queryParams.append("valid=true&");
            }
            if (filters.invalid() != null && filters.invalid()) {
                queryParams.append("invalid=true&");
            }
            if (filters.catchall() != null && filters.catchall()) {
                queryParams.append("catchall=true&");
            }
            if (filters.role() != null && filters.role()) {
                queryParams.append("role=true&");
            }
            if (filters.unknown() != null && filters.unknown()) {
                queryParams.append("unknown=true&");
            }
            if (filters.disposable() != null && filters.disposable()) {
                queryParams.append("disposable=true&");
            }
            if (filters.risky() != null && filters.risky()) {
                queryParams.append("risky=true&");
            }

            if (queryParams.length() > 0) {
                // Remove trailing &
                queryParams.setLength(queryParams.length() - 1);
                path.append("?").append(queryParams);
            }
        }

        return baseUrl + path;
    }

    /**
     * Download file verification results as a byte array.
     */
    public byte[] downloadFileResults(String jobId) throws EmailVerifyException {
        return downloadFileResults(jobId, null);
    }

    /**
     * Download file verification results as a byte array with filters.
     */
    public byte[] downloadFileResults(String jobId, ResultFilters filters) throws EmailVerifyException {
        String url = getFileResultsUrl(jobId, filters);

        Request request = new Request.Builder()
            .url(url)
            .header("EV-API-KEY", apiKey)
            .header("User-Agent", USER_AGENT)
            .get()
            .build();

        try {
            Response response = httpClient.newCall(request).execute();
            int statusCode = response.code();

            if (statusCode == 307) {
                // Handle redirect
                String location = response.header("Location");
                if (location != null) {
                    Request redirectRequest = new Request.Builder()
                        .url(location)
                        .get()
                        .build();
                    response = httpClient.newCall(redirectRequest).execute();
                }
            }

            if (response.isSuccessful() && response.body() != null) {
                return response.body().bytes();
            }

            String responseString = response.body() != null ? response.body().string() : "";
            return handleErrorResponse(statusCode, responseString, response, "GET",
                "/v1/verify/file/" + jobId + "/results", null, byte[].class, null, 1, true);
        } catch (IOException e) {
            throw new EmailVerifyException("Network error: " + e.getMessage(), "NETWORK_ERROR", 0);
        }
    }

    // ==================== Credits ====================

    /**
     * Get current credit balance.
     */
    public CreditsResponse getCredits() throws EmailVerifyException {
        return request("GET", "/v1/credits", null, CreditsResponse.class);
    }

    // ==================== Webhooks ====================

    /**
     * Create a new webhook.
     * Note: The secret is returned by the API and should be stored securely for signature verification.
     *
     * @param url    HTTPS URL to receive webhook notifications
     * @param events Events to subscribe to (use WebhookEvent constants)
     */
    public Webhook createWebhook(String url, List<String> events) throws EmailVerifyException {
        Map<String, Object> payload = new HashMap<>();
        payload.put("url", url);
        payload.put("events", events);

        return request("POST", "/v1/webhooks", payload, Webhook.class);
    }

    /**
     * List all webhooks.
     */
    public List<Webhook> listWebhooks() throws EmailVerifyException {
        return request("GET", "/v1/webhooks", null, new TypeReference<>() {});
    }

    /**
     * Delete a webhook.
     */
    public void deleteWebhook(String webhookId) throws EmailVerifyException {
        request("DELETE", "/v1/webhooks/" + webhookId, null, Void.class);
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
