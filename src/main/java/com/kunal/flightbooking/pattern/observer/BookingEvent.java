package com.kunal.flightbooking.pattern.observer;

import com.kunal.flightbooking.domain.Booking;
import com.kunal.flightbooking.domain.BookingStatus;

import java.math.BigDecimal;

/**
 * Immutable domain event published whenever a booking reaches a notable state. Listeners (observers)
 * subscribe through Spring's ApplicationEvent infrastructure, so publishers never know who is listening.
 */
public record BookingEvent(Long bookingId, String pnr, BookingStatus status, String customerName,
                           String email, String phone, BigDecimal amount, String reason) {

    public static BookingEvent of(Booking booking) {
        return new BookingEvent(booking.getId(), booking.getPnr(), booking.getStatus(),
                booking.getUser().getFullName(), booking.getUser().getEmail(), booking.getUser().getPhone(),
                booking.getTotalAmount(), booking.getFailureReason());
    }
}
