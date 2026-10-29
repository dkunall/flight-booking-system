package com.kunal.flightbooking.unit;

import com.kunal.flightbooking.domain.*;
import com.kunal.flightbooking.dto.request.PaymentRequest;
import com.kunal.flightbooking.dto.response.PaymentResponse;
import com.kunal.flightbooking.exception.BusinessException;
import com.kunal.flightbooking.exception.HoldExpiredException;
import com.kunal.flightbooking.exception.InvalidStateTransitionException;
import com.kunal.flightbooking.exception.ResourceNotFoundException;
import com.kunal.flightbooking.pattern.factory.CardPaymentGateway;
import com.kunal.flightbooking.pattern.factory.PaymentGatewayFactory;
import com.kunal.flightbooking.pattern.factory.UpiPaymentGateway;
import com.kunal.flightbooking.pattern.observer.BookingEvent;
import com.kunal.flightbooking.repository.BookingRepository;
import com.kunal.flightbooking.repository.PaymentRepository;
import com.kunal.flightbooking.repository.SeatRepository;
import com.kunal.flightbooking.service.PaymentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import com.kunal.flightbooking.service.OutboxWriter;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    @Mock BookingRepository bookingRepository;
    @Mock SeatRepository seatRepository;
    @Mock PaymentRepository paymentRepository;
    @Mock OutboxWriter events;

    private final Clock clock = Clock.fixed(Instant.parse("2026-10-20T06:00:00Z"), ZoneId.of("Asia/Kolkata"));
    private final LocalDateTime now = LocalDateTime.now(clock);

    private PaymentService service;
    private Seat seat;
    private Booking booking;

    @BeforeEach
    void setUp() {
        PaymentGatewayFactory factory =
                new PaymentGatewayFactory(List.of(new UpiPaymentGateway(), new CardPaymentGateway()));
        service = new PaymentService(bookingRepository, seatRepository, paymentRepository, factory, events, clock);
        FlightSchedule schedule = Fixtures.schedule(now.plusDays(2), 0, 60);
        seat = Fixtures.seat(schedule, 11, "1A");
        booking = Fixtures.heldBooking(42, Fixtures.user(5), schedule, now.plusMinutes(10), seat);
    }

    private void stubHappyPath() {
        when(bookingRepository.findByIdForUpdate(42L)).thenReturn(Optional.of(booking));
        when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> Fixtures.withId(inv.getArgument(0), 9L));
        when(seatRepository.findByBookingIdForUpdate(42L)).thenReturn(List.of(seat));
    }

    @Test
    void successfulUpiPaymentConfirmsBookingAndBooksSeats() {
        stubHappyPath();

        PaymentResponse response = service.pay(42L, 5L, new PaymentRequest(PaymentMethod.UPI, "kunal@okaxis", null));

        assertThat(response.status()).isEqualTo(PaymentStatus.SUCCESS);
        assertThat(response.bookingStatus()).isEqualTo(BookingStatus.CONFIRMED);
        assertThat(response.gatewayReference()).startsWith("UPI-");
        assertThat(seat.getStatus()).isEqualTo(SeatStatus.BOOKED);
        verify(events).append(any(BookingEvent.class));
    }

    @Test
    void declinedCardFailsBookingAndReleasesSeats() {
        stubHappyPath();

        PaymentResponse response = service.pay(42L, 5L, new PaymentRequest(PaymentMethod.CARD, null, "tok_fail"));

        assertThat(response.status()).isEqualTo(PaymentStatus.FAILED);
        assertThat(response.bookingStatus()).isEqualTo(BookingStatus.FAILED);
        assertThat(booking.getFailureReason()).contains("CARD_DECLINED");
        assertThat(seat.isAvailable()).isTrue();
    }

    @Test
    void invalidPaymentDetailsAreRejectedWithoutChangingState() {
        when(bookingRepository.findByIdForUpdate(42L)).thenReturn(Optional.of(booking));

        assertThatThrownBy(() -> service.pay(42L, 5L, new PaymentRequest(PaymentMethod.UPI, "bad", null)))
                .isInstanceOf(BusinessException.class);
        assertThat(booking.getStatus()).isEqualTo(BookingStatus.SEATS_HELD);
        verifyNoInteractions(paymentRepository);
    }

    @Test
    void payingAConfirmedBookingIsAnInvalidTransition() {
        stubHappyPath();
        service.pay(42L, 5L, new PaymentRequest(PaymentMethod.UPI, "kunal@okaxis", null));

        assertThatThrownBy(() -> service.pay(42L, 5L, new PaymentRequest(PaymentMethod.UPI, "kunal@okaxis", null)))
                .isInstanceOf(InvalidStateTransitionException.class);
    }

    @Test
    void expiredHoldCannotBePaid() {
        Booking expired = Fixtures.heldBooking(43, Fixtures.user(5), Fixtures.schedule(now.plusDays(2), 0, 60),
                now.minusSeconds(1), Fixtures.seat(Fixtures.schedule(now.plusDays(2), 0, 60), 12, "1B"));
        when(bookingRepository.findByIdForUpdate(43L)).thenReturn(Optional.of(expired));

        assertThatThrownBy(() -> service.pay(43L, 5L, new PaymentRequest(PaymentMethod.UPI, "kunal@okaxis", null)))
                .isInstanceOf(HoldExpiredException.class);
    }

    @Test
    void otherUsersCannotPayForMyBooking() {
        when(bookingRepository.findByIdForUpdate(42L)).thenReturn(Optional.of(booking));

        assertThatThrownBy(() -> service.pay(42L, 999L, new PaymentRequest(PaymentMethod.UPI, "x@okaxis", null)))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
