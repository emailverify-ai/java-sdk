package com.emailverify.sdk.exception;

public class EmailVerifyException extends Exception {
    private final String errorCode;
    private final int statusCode;
    private final String details;

    public EmailVerifyException(String message, String errorCode, int statusCode) {
        this(message, errorCode, statusCode, null);
    }

    public EmailVerifyException(String message, String errorCode, int statusCode, String details) {
        super(message);
        this.errorCode = errorCode;
        this.statusCode = statusCode;
        this.details = details;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public int getStatusCode() {
        return statusCode;
    }

    public String getDetails() {
        return details;
    }

    @Override
    public String toString() {
        return errorCode + ": " + getMessage();
    }
}
