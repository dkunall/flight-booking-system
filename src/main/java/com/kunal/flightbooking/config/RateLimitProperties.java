package com.kunal.flightbooking.config;

import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** Limits for the public, unauthenticated search endpoints. */
@Validated
@ConfigurationProperties(prefix = "app.rate-limit.search")
public record RateLimitProperties(boolean enabled, @Positive int capacity, @Positive double refillPerSecond,
                                  @Positive int maxClients) {
}
