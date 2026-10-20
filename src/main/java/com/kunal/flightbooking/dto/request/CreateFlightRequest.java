package com.kunal.flightbooking.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateFlightRequest(
        @NotBlank @Pattern(regexp = "^[A-Z0-9]{2}\\d{1,4}$", message = "e.g. 6E2134") String flightNumber,
        @NotBlank @Pattern(regexp = "^[A-Z0-9]{2}$") String airlineCode,
        @NotBlank @Pattern(regexp = "^[A-Z]{3}$") String sourceCode,
        @NotBlank @Pattern(regexp = "^[A-Z]{3}$") String destinationCode,
        @NotBlank @Size(max = 50) String aircraftModel,
        @Min(0) @Max(60) int businessSeats,
        @Min(1) @Max(300) int economySeats) {
}
