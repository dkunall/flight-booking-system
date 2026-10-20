package com.kunal.flightbooking.pattern.strategy;

import com.kunal.flightbooking.domain.SeatClass;

import java.math.BigDecimal;

/** Immutable input shared by every fare strategy. */
public record FareContext(SeatClass seatClass, BigDecimal baseFare, int totalSeats, int availableSeats,
                          String couponCode) {

    /** Fraction of the cabin already sold or held, 0.0 to 1.0. */
    public double occupancy() {
        if (totalSeats <= 0) {
            return 0.0;
        }
        int occupied = Math.max(0, totalSeats - availableSeats);
        return (double) occupied / totalSeats;
    }

    public boolean hasCoupon() {
        return couponCode != null && !couponCode.isBlank();
    }
}
