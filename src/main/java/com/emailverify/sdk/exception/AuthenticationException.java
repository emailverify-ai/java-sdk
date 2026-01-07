package com.emailverify.sdk.exception;

public class AuthenticationException extends EmailVerifyException {
    public AuthenticationException() {
        this("Invalid or missing API key");
    }

    public AuthenticationException(String message) {
        super(message, "INVALID_API_KEY", 401);
    }
}
