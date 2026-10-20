package com.kunal.flightbooking.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateAirportRequest(
        @NotBlank @Pattern(regexp = "^[A-Z]{3}$", message = "must be a 3-letter IATA code") String code,
        @NotBlank @Size(max = 120) String name,
        @NotBlank @Size(max = 80) String city,
        @NotBlank @Size(max = 80) String country) {
}
