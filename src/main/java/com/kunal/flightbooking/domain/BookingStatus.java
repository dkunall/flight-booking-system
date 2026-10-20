package com.kunal.flightbooking.domain;

/**
 * Booking lifecycle states. Allowed transitions live in
 * {@link com.kunal.flightbooking.pattern.state.BookingStateMachine}.
 */
public enum BookingStatus {
    INITIATED,
    SEATS_HELD,
    PAYMENT_PENDING,
    CONFIRMED,
    FAILED,
    CANCELLED,
    REFUNDED
}
