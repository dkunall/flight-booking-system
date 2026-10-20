package com.kunal.flightbooking.dto.response;

import com.kunal.flightbooking.domain.BookingStatus;
import com.kunal.flightbooking.domain.PaymentMethod;
import com.kunal.flightbooking.domain.PaymentStatus;

import java.math.BigDecimal;

public record PaymentResponse(Long paymentId, Long bookingId, String pnr, PaymentMethod method,
                              PaymentStatus status, BigDecimal amount, String gatewayReference,
                              String failureReason, BookingStatus bookingStatus) {
}
