package com.kunal.flightbooking.exception;

import org.springframework.http.HttpStatus;

public class HoldExpiredException extends BusinessException {
    public HoldExpiredException(String pnr) {
        super(HttpStatus.GONE, "HOLD_EXPIRED", "Seat hold for booking " + pnr + " has expired");
    }
}
