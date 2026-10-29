package com.kunal.flightbooking.dto.response;

import com.kunal.flightbooking.domain.BookingStatus;
import com.kunal.flightbooking.domain.SeatClass;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record BookingResponse(
        Long id,
        String pnr,
        BookingStatus status,
        Long scheduleId,
        String flightNumber,
        String sourceCode,
        String destinationCode,
        LocalDateTime departureTime,
        SeatClass seatClass,
        BigDecimal totalAmount,
        String couponCode,
        LocalDateTime holdExpiresAt,
        String failureReason,
        List<PassengerResponse> passengers,
        LocalDateTime createdAt) implements Serializable {
}
