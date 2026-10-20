package com.kunal.flightbooking.dto.response;

import com.kunal.flightbooking.domain.Gender;

import java.math.BigDecimal;

public record PassengerResponse(String fullName, int age, Gender gender, Long seatId, String seatNumber,
                                BigDecimal fare) {
}
