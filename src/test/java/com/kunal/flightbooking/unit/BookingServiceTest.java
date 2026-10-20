package com.kunal.flightbooking.unit;

import com.kunal.flightbooking.config.BookingProperties;
import com.kunal.flightbooking.domain.*;
import com.kunal.flightbooking.dto.request.CreateBookingRequest;
import com.kunal.flightbooking.dto.request.PassengerRequest;
import com.kunal.flightbooking.dto.response.BookingResponse;
import com.kunal.flightbooking.exception.BusinessException;
import com.kunal.flightbooking.exception.InvalidStateTransitionException;
import com.kunal.flightbooking.exception.SeatUnavailableException;
import com.kunal.flightbooking.pattern.factory.PaymentGateway;
import com.kunal.flightbooking.pattern.factory.PaymentGatewayFactory;
import com.kunal.flightbooking.pattern.observer.BookingEvent;
import com.kunal.flightbooking.pattern.strategy.BaseFareStrategy;
import com.kunal.flightbooking.pattern.strategy.CouponDiscountStrategy;
import com.kunal.flightbooking.pattern.strategy.FareCalculator;
import com.kunal.flightbooking.pattern.strategy.OccupancyPricingStrategy;
import com.kunal.flightbooking.repository.*;
import com.kunal.flightbooking.service.BookingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BookingServiceTest {

    @Mock BookingRepository bookingRepository;
    @Mock SeatRepository seatRepository;
    @Mock FlightScheduleRepository scheduleRepository;
    @Mock UserRepository userRepository;
    @Mock PaymentRepository paymentRepository;
    @Mock PaymentGatewayFactory gatewayFactory;
    @Mock ApplicationEventPublisher events;

    private final ZoneId zone = ZoneId.of("Asia/Kolkata");
    private final Clock clock = Clock.fixed(Instant.parse("2026-10-20T06:00:00Z"), zone);
    private final LocalDateTime now = LocalDateTime.now(clock);

    private BookingService service;
    private FlightSchedule schedule;
    private User user;

    @BeforeEach
    void setUp() {
        FareCalculator fares = FareCalculator.of(new BaseFareStrategy(), new OccupancyPricingStrategy(),
                new CouponDiscountStrategy());
        service = new BookingService(bookingRepository, seatRepository, scheduleRepository, userRepository,
                paymentRepository, fares, gatewayFactory, events, new BookingProperties(10, 9), clock);
        schedule = Fixtures.schedule(now.plusDays(3), 0, 100);
        user = Fixtures.user(5);
    }

    private static CreateBookingRequest request(String coupon, Long... seatIds) {
        return new CreateBookingRequest(1L, java.util.Arrays.stream(seatIds)
                .map(id -> new PassengerRequest("Passenger " + id, 28, Gender.MALE, id)).toList(), coupon);
    }

    @Test
    void createBookingLocksAndHoldsSeatsForTenMinutes() {
        Seat s1 = Fixtures.seat(schedule, 11, "1A");
        Seat s2 = Fixtures.seat(schedule, 12, "1B");
        when(scheduleRepository.findDetailedById(1L)).thenReturn(Optional.of(schedule));
        when(userRepository.findById(5L)).thenReturn(Optional.of(user));
        when(seatRepository.findAllByIdForUpdate(List.of(11L, 12L))).thenReturn(List.of(s1, s2));
        when(seatRepository.countByScheduleAndClassAndStatus(1L, SeatClass.ECONOMY, SeatStatus.AVAILABLE))
                .thenReturn(100L);
        when(bookingRepository.save(any(Booking.class))).thenAnswer(inv -> Fixtures.withId(inv.getArgument(0), 42L));

        BookingResponse response = service.createBooking(5L, request("FLY10", 11L, 12L));

        assertThat(response.status()).isEqualTo(BookingStatus.SEATS_HELD);
        assertThat(response.totalAmount()).isEqualByComparingTo("9000.00");   // 2 x (5000 - 10%)
        assertThat(response.couponCode()).isEqualTo("FLY10");
        assertThat(response.holdExpiresAt()).isEqualTo(now.plusMinutes(10));
        assertThat(List.of(s1, s2)).allSatisfy(s -> {
            assertThat(s.getStatus()).isEqualTo(SeatStatus.HELD);
            assertThat(s.getBookingId()).isEqualTo(42L);
        });
        ArgumentCaptor<BookingEvent> event = ArgumentCaptor.forClass(BookingEvent.class);
        verify(events).publishEvent(event.capture());
        assertThat(event.getValue().status()).isEqualTo(BookingStatus.SEATS_HELD);
    }

    @Test
    void createBookingFailsWhenSeatAlreadyHeld() {
        Seat taken = Fixtures.seat(schedule, 11, "1A");
        taken.hold(99L, now.plusMinutes(5));
        when(scheduleRepository.findDetailedById(1L)).thenReturn(Optional.of(schedule));
        when(userRepository.findById(5L)).thenReturn(Optional.of(user));
        when(seatRepository.findAllByIdForUpdate(anyCollection())).thenReturn(List.of(taken));

        assertThatThrownBy(() -> service.createBooking(5L, request(null, 11L)))
                .isInstanceOf(SeatUnavailableException.class);
        verify(bookingRepository, never()).save(any());
        verifyNoInteractions(events);
    }

    @Test
    void createBookingRejectsDuplicateSeatsBeforeLocking() {
        when(scheduleRepository.findDetailedById(1L)).thenReturn(Optional.of(schedule));

        assertThatThrownBy(() -> service.createBooking(5L, request(null, 11L, 11L)))
                .isInstanceOf(BusinessException.class).hasMessageContaining("different seat");
        verifyNoInteractions(seatRepository);
    }

    @Test
    void createBookingRejectsDepartedFlight() {
        FlightSchedule departed = Fixtures.schedule(now.minusHours(1), 0, 100);
        when(scheduleRepository.findDetailedById(1L)).thenReturn(Optional.of(departed));

        assertThatThrownBy(() -> service.createBooking(5L, request(null, 11L)))
                .isInstanceOf(BusinessException.class)
                .extracting("code").isEqualTo("SCHEDULE_NOT_BOOKABLE");
    }

    @Test
    void cancellingConfirmedBookingReleasesSeatsAndRefunds() {
        Seat seat = Fixtures.seat(schedule, 11, "1A");
        Booking booking = Fixtures.heldBooking(42, user, schedule, now.plusMinutes(10), seat);
        booking.transitionTo(BookingStatus.PAYMENT_PENDING);
        seat.confirm(42L);
        booking.transitionTo(BookingStatus.CONFIRMED);
        Payment payment = Fixtures.withId(new Payment(booking, PaymentMethod.UPI, booking.getTotalAmount()), 7);
        payment.markSuccess("UPI-REF-1");
        PaymentGateway gateway = mock(PaymentGateway.class);

        when(bookingRepository.findByIdForUpdate(42L)).thenReturn(Optional.of(booking));
        when(seatRepository.findByBookingIdForUpdate(42L)).thenReturn(List.of(seat));
        when(paymentRepository.findFirstByBookingIdAndStatus(42L, PaymentStatus.SUCCESS)).thenReturn(Optional.of(payment));
        when(gatewayFactory.get(PaymentMethod.UPI)).thenReturn(gateway);
        when(gateway.refund("UPI-REF-1", new BigDecimal("5000.00")))
                .thenReturn(PaymentGateway.GatewayResponse.ok("UPI-REF-1"));

        BookingResponse response = service.cancelBooking(42L, 5L);

        assertThat(response.status()).isEqualTo(BookingStatus.REFUNDED);
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.REFUNDED);
        assertThat(seat.isAvailable()).isTrue();
    }

    @Test
    void cancellingHeldBookingNeedsNoRefund() {
        Seat seat = Fixtures.seat(schedule, 11, "1A");
        Booking booking = Fixtures.heldBooking(42, user, schedule, now.plusMinutes(10), seat);
        when(bookingRepository.findByIdForUpdate(42L)).thenReturn(Optional.of(booking));
        when(seatRepository.findByBookingIdForUpdate(42L)).thenReturn(List.of(seat));

        assertThat(service.cancelBooking(42L, 5L).status()).isEqualTo(BookingStatus.CANCELLED);
        assertThat(seat.isAvailable()).isTrue();
        verifyNoInteractions(paymentRepository, gatewayFactory);
    }

    @Test
    void cancellingTwiceIsAnInvalidTransition() {
        Seat seat = Fixtures.seat(schedule, 11, "1A");
        Booking booking = Fixtures.heldBooking(42, user, schedule, now.plusMinutes(10), seat);
        booking.transitionTo(BookingStatus.CANCELLED);
        when(bookingRepository.findByIdForUpdate(42L)).thenReturn(Optional.of(booking));

        assertThatThrownBy(() -> service.cancelBooking(42L, 5L)).isInstanceOf(InvalidStateTransitionException.class);
    }

    @Test
    void expireHoldFailsBookingAndReleasesSeats() {
        Seat seat = Fixtures.seat(schedule, 11, "1A");
        Booking booking = Fixtures.heldBooking(42, user, schedule, now.minusSeconds(1), seat);
        when(bookingRepository.findByIdForUpdate(42L)).thenReturn(Optional.of(booking));
        when(seatRepository.findByBookingIdForUpdate(42L)).thenReturn(List.of(seat));

        assertThat(service.expireHold(42L)).isTrue();
        assertThat(booking.getStatus()).isEqualTo(BookingStatus.FAILED);
        assertThat(booking.getFailureReason()).isEqualTo("HOLD_EXPIRED");
        assertThat(seat.isAvailable()).isTrue();
    }

    @Test
    void expireHoldIgnoresBookingThatIsStillValid() {
        Seat seat = Fixtures.seat(schedule, 11, "1A");
        Booking booking = Fixtures.heldBooking(42, user, schedule, now.plusMinutes(3), seat);
        when(bookingRepository.findByIdForUpdate(42L)).thenReturn(Optional.of(booking));

        assertThat(service.expireHold(42L)).isFalse();
        assertThat(booking.getStatus()).isEqualTo(BookingStatus.SEATS_HELD);
        verifyNoInteractions(seatRepository);
    }
}
