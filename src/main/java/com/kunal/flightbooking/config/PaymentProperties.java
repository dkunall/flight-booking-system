package com.kunal.flightbooking.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** Shared secret the (mock) gateway uses to sign callbacks. Comes from PAYMENT_WEBHOOK_SECRET. */
@Validated
@ConfigurationProperties(prefix = "app.payments")
public record PaymentProperties(
        @NotBlank(message = "set PAYMENT_WEBHOOK_SECRET")
        @Size(min = 32, message = "PAYMENT_WEBHOOK_SECRET must be at least 32 chars")
        String webhookSecret) {
}
