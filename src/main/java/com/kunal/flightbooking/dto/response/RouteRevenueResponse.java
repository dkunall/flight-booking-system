package com.kunal.flightbooking.dto.response;

import java.math.BigDecimal;

public record RouteRevenueResponse(String sourceCode, String destinationCode, long bookings, long passengers,
                                   BigDecimal revenue) {
}
