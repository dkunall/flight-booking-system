package com.kunal.flightbooking.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record CreateScheduleRequest(
        @NotNull Long flightId,
        @NotNull @Future LocalDateTime departureTime,
        @NotNull @Future LocalDateTime arrivalTime,
        @NotNull @DecimalMin("1.00") @Digits(integer = 8, fraction = 2) BigDecimal economyBaseFare,
        @NotNull @DecimalMin("1.00") @Digits(integer = 8, fraction = 2) BigDecimal businessBaseFare) {
}
