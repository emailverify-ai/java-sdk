package com.emailverify.sdk.exception;

public class ValidationException extends EmailVerifyException {
    public ValidationException(String message) {
        this(message, null);
    }

    public ValidationException(String message, String details) {
        super(message, "INVALID_REQUEST", 400, details);
    }
}
