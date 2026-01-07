package com.emailverify.sdk.exception;

public class RateLimitException extends EmailVerifyException {
    private final int retryAfter;

    public RateLimitException() {
        this("Rate limit exceeded", 0);
    }

    public RateLimitException(String message, int retryAfter) {
        super(message, "RATE_LIMIT_EXCEEDED", 429);
        this.retryAfter = retryAfter;
    }

    public int getRetryAfter() {
        return retryAfter;
    }
}
