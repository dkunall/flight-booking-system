package com.kunal.flightbooking.dto.response;

public record FlightResponse(Long id, String flightNumber, String airlineCode, String sourceCode,
                             String destinationCode, String aircraftModel, int businessSeats, int economySeats) {
}
