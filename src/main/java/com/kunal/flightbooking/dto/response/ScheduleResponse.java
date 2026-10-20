package com.kunal.flightbooking.dto.response;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record ScheduleResponse(Long id, String flightNumber, String sourceCode, String destinationCode,
                               LocalDateTime departureTime, LocalDateTime arrivalTime,
                               BigDecimal economyBaseFare, BigDecimal businessBaseFare, int seatsGenerated) {
}
