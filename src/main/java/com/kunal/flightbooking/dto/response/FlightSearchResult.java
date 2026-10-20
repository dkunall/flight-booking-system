package com.kunal.flightbooking.dto.response;

import com.kunal.flightbooking.domain.SeatClass;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Serializable so results can be cached in Redis with the default JDK serializer. */
public record FlightSearchResult(
        Long scheduleId,
        String flightNumber,
        String airlineCode,
        String airlineName,
        String sourceCode,
        String destinationCode,
        LocalDateTime departureTime,
        LocalDateTime arrivalTime,
        long durationMinutes,
        SeatClass seatClass,
        int availableSeats,
        BigDecimal farePerPassenger,
        BigDecimal totalFare) implements Serializable {
}
