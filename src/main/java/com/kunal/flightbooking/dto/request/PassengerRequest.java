package com.kunal.flightbooking.dto.request;

import com.kunal.flightbooking.domain.Gender;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record PassengerRequest(
        @NotBlank @Size(max = 100) String fullName,
        @NotNull @Min(0) @Max(120) Integer age,
        @NotNull Gender gender,
        @NotNull Long seatId) {
}
