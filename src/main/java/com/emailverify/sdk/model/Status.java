package com.emailverify.sdk.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Email verification status.
 */
public enum Status {
    VALID("valid"),
    INVALID("invalid"),
    UNKNOWN("unknown"),
    RISKY("risky"),
    DISPOSABLE("disposable"),
    CATCHALL("catchall"),
    ROLE("role");

    private final String value;

    Status(String value) {
        this.value = value;
    }

    @JsonValue
    public String getValue() {
        return value;
    }

    @JsonCreator
    public static Status fromValue(String value) {
        for (Status status : Status.values()) {
            if (status.value.equalsIgnoreCase(value)) {
                return status;
            }
        }
        return UNKNOWN;
    }

    @Override
    public String toString() {
        return value;
    }
}
