package com.emailverify.sdk.exception;

public class NotFoundException extends EmailVerifyException {
    public NotFoundException() {
        this("Resource not found");
    }

    public NotFoundException(String message) {
        super(message, "NOT_FOUND", 404);
    }
}
