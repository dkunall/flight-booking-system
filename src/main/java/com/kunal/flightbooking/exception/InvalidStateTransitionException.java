package com.kunal.flightbooking.exception;

import com.kunal.flightbooking.domain.BookingStatus;
import org.springframework.http.HttpStatus;

public class InvalidStateTransitionException extends BusinessException {
    public InvalidStateTransitionException(BookingStatus from, BookingStatus to) {
        super(HttpStatus.CONFLICT, "INVALID_STATE_TRANSITION",
                "Booking cannot move from " + from + " to " + to);
    }
}
