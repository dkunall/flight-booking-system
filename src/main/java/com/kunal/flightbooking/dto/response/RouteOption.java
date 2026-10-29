package com.kunal.flightbooking.dto.response;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/** One itinerary: one leg for a direct flight, more for connections. Each leg is booked separately. */
public record RouteOption(
        int stops,
        List<String> via,
        LocalDateTime departureTime,
        LocalDateTime arrivalTime,
        long totalDurationMinutes,
        List<Long> layoverMinutes,
        BigDecimal farePerPassenger,
        BigDecimal totalFare,
        List<FlightSearchResult> legs) implements Serializable {
}
