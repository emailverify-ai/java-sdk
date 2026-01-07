package com.emailverify.sdk.exception;

public class InsufficientCreditsException extends EmailVerifyException {
    public InsufficientCreditsException() {
        this("Insufficient credits");
    }

    public InsufficientCreditsException(String message) {
        super(message, "INSUFFICIENT_CREDITS", 403);
    }
}
