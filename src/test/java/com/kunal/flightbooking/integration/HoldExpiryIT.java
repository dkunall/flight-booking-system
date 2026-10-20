package com.kunal.flightbooking.integration;

import com.kunal.flightbooking.domain.*;
import com.kunal.flightbooking.dto.request.CreateBookingRequest;
import com.kunal.flightbooking.dto.request.PassengerRequest;
import com.kunal.flightbooking.dto.request.PaymentRequest;
import com.kunal.flightbooking.exception.HoldExpiredException;
import com.kunal.flightbooking.repository.BookingRepository;
import com.kunal.flightbooking.repository.SeatRepository;
import com.kunal.flightbooking.service.BookingService;
import com.kunal.flightbooking.service.PaymentService;
import com.kunal.flightbooking.service.scheduler.HoldExpiryScheduler;
import com.kunal.flightbooking.support.IntegrationTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HoldExpiryIT extends IntegrationTestBase {

    @Autowired BookingService bookingService;
    @Autowired PaymentService paymentService;
    @Autowired HoldExpiryScheduler scheduler;
    @Autowired BookingRepository bookingRepository;
    @Autowired SeatRepository seatRepository;
    @Autowired JdbcTemplate jdbc;

    @Test
    void schedulerReleasesExpiredHoldsAndExpiredBookingsCannotBePaid() {
        var schedule = data.simpleSchedule();
        List<Long> seats = data.seatIds(schedule.id(), SeatClass.ECONOMY);
        User user = data.user(Role.USER);
        var expired = bookingService.createBooking(user.getId(), new CreateBookingRequest(schedule.id(),
                List.of(new PassengerRequest("Late Payer", 30, Gender.MALE, seats.get(0))), null));
        var fresh = bookingService.createBooking(user.getId(), new CreateBookingRequest(schedule.id(),
                List.of(new PassengerRequest("On Time", 30, Gender.MALE, seats.get(1))), null));

        // Simulate the 10 minutes passing for the first booking only.
        jdbc.update("UPDATE bookings SET hold_expires_at = DATEADD('MINUTE', -1, CURRENT_TIMESTAMP) WHERE id = ?",
                expired.id());

        assertThatThrownBy(() -> paymentService.pay(expired.id(), user.getId(),
                new PaymentRequest(PaymentMethod.UPI, "late@okaxis", null)))
                .isInstanceOf(HoldExpiredException.class);

        assertThat(scheduler.releaseExpiredHolds()).isGreaterThanOrEqualTo(1);

        Booking expiredBooking = bookingRepository.findById(expired.id()).orElseThrow();
        assertThat(expiredBooking.getStatus()).isEqualTo(BookingStatus.FAILED);
        assertThat(expiredBooking.getFailureReason()).isEqualTo("HOLD_EXPIRED");
        assertThat(seatRepository.findById(seats.get(0)).orElseThrow().getStatus()).isEqualTo(SeatStatus.AVAILABLE);

        assertThat(bookingRepository.findById(fresh.id()).orElseThrow().getStatus()).isEqualTo(BookingStatus.SEATS_HELD);
        assertThat(seatRepository.findById(seats.get(1)).orElseThrow().getStatus()).isEqualTo(SeatStatus.HELD);

        // The released seat is immediately bookable again.
        var rebooked = bookingService.createBooking(data.user(Role.USER).getId(), new CreateBookingRequest(
                schedule.id(), List.of(new PassengerRequest("Next", 22, Gender.FEMALE, seats.get(0))), null));
        assertThat(rebooked.status()).isEqualTo(BookingStatus.SEATS_HELD);
    }
}
