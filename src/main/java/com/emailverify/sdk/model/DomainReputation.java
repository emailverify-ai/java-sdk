package com.emailverify.sdk.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * Domain reputation information.
 */
public record DomainReputation(
    @JsonProperty("mx_ip") String mxIp,
    @JsonProperty("is_listed") boolean isListed,
    @JsonProperty("blacklists") List<String> blacklists,
    @JsonProperty("checked") boolean checked
) {}
