package com.kunal.flightbooking.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** The app refuses to start without a JWT_SECRET of at least 32 chars (HS256 needs 256 bits). */
@Validated
@ConfigurationProperties(prefix = "app.jwt")
public record JwtProperties(
        @NotBlank(message = "set JWT_SECRET") @Size(min = 32, message = "JWT_SECRET must be at least 32 chars")
        String secret,
        @Positive long expirationMinutes) {
}
