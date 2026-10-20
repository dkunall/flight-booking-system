package com.kunal.flightbooking.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/** Creating a booking = choosing seats and holding them for 10 minutes. */
public record CreateBookingRequest(
        @NotNull Long scheduleId,
        @NotEmpty @Size(max = 9) List<@Valid @NotNull PassengerRequest> passengers,
        @Size(max = 20) String couponCode) {
}
