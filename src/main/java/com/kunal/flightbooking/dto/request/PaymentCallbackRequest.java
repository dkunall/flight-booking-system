package com.kunal.flightbooking.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record PaymentCallbackRequest(
        @NotBlank @Size(max = 64) String gatewayReference,
        @NotBlank @Pattern(regexp = "SUCCESS|FAILED") String status,
        @Size(max = 200) String failureReason) {
}
