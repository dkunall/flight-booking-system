package com.kunal.flightbooking.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateAirlineRequest(
        @NotBlank @Pattern(regexp = "^[A-Z0-9]{2}$", message = "must be a 2-character IATA airline code") String code,
        @NotBlank @Size(max = 100) String name) {
}
