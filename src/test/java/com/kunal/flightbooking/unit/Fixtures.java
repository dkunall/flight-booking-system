package com.kunal.flightbooking.unit;

import com.kunal.flightbooking.domain.*;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** In-memory domain objects with ids, for Mockito-based service tests. */
final class Fixtures {

    private Fixtures() {
    }

    static <T> T withId(T entity, long id) {
        ReflectionTestUtils.setField(entity, "id", id);
        return entity;
    }

    static User user(long id) {
        return withId(new User("Test User " + id, "user" + id + "@example.com", "{hash}", "9876543210", Role.USER), id);
    }

    static FlightSchedule schedule(LocalDateTime departure, int businessSeats, int economySeats) {
        Airline airline = withId(new Airline("6E", "IndiGo"), 1);
        Airport del = withId(new Airport("DEL", "Delhi", "New Delhi", "India"), 1);
        Airport bom = withId(new Airport("BOM", "Mumbai", "Mumbai", "India"), 2);
        Flight flight = withId(new Flight("6E2134", airline, del, bom, "A320", businessSeats, economySeats), 1);
        return withId(new FlightSchedule(flight, departure, departure.plusMinutes(130),
                new BigDecimal("5000.00"), new BigDecimal("15000.00")), 1);
    }

    static Seat seat(FlightSchedule schedule, long id, String number) {
        return withId(new Seat(schedule, number, SeatClass.ECONOMY), id);
    }

    /** A booking already in SEATS_HELD with the given seats held until {@code holdUntil}. */
    static Booking heldBooking(long id, User user, FlightSchedule schedule, LocalDateTime holdUntil, Seat... seats) {
        Booking booking = withId(new Booking("PNR" + id, user, schedule, SeatClass.ECONOMY), id);
        BigDecimal fare = new BigDecimal("5000.00");
        for (Seat seat : seats) {
            seat.hold(id, holdUntil);
            booking.addPassenger(new Passenger("Passenger " + seat.getSeatNumber(), 30, Gender.FEMALE, seat, fare));
        }
        booking.setTotalAmount(fare.multiply(BigDecimal.valueOf(seats.length)));
        booking.setHoldExpiresAt(holdUntil);
        booking.transitionTo(BookingStatus.SEATS_HELD);
        return booking;
    }
}
