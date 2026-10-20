package com.kunal.flightbooking.dto.request;

import com.kunal.flightbooking.domain.PaymentMethod;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** upiId is required for UPI; cardToken (a tokenised card, never a raw card number) for CARD. */
public record PaymentRequest(
        @NotNull PaymentMethod method,
        @Size(max = 100) String upiId,
        @Size(max = 64) String cardToken) {
}
