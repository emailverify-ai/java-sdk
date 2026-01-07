package com.emailverify.sdk.model;

import com.fasterxml.jackson.annotation.JsonProperty;

public record VerificationResult(
    @JsonProperty("deliverable") boolean deliverable,
    @JsonProperty("valid_format") boolean validFormat,
    @JsonProperty("valid_domain") boolean validDomain,
    @JsonProperty("valid_mx") boolean validMx,
    @JsonProperty("disposable") boolean disposable,
    @JsonProperty("role") boolean role,
    @JsonProperty("catchall") boolean catchall,
    @JsonProperty("free") boolean free,
    @JsonProperty("smtp_valid") boolean smtpValid
) {}
