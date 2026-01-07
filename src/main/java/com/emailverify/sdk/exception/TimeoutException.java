package com.emailverify.sdk.exception;

public class TimeoutException extends EmailVerifyException {
    public TimeoutException() {
        this("Request timed out");
    }

    public TimeoutException(String message) {
        super(message, "TIMEOUT", 504);
    }
}
