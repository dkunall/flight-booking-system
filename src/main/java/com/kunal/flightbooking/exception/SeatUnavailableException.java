package com.kunal.flightbooking.exception;

import org.springframework.http.HttpStatus;

public class SeatUnavailableException extends BusinessException {
    public SeatUnavailableException(String seatNumber) {
        super(HttpStatus.CONFLICT, "SEAT_UNAVAILABLE", "Seat " + seatNumber + " is no longer available");
    }
}
