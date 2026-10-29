package com.kunal.flightbooking.service;

import com.kunal.flightbooking.config.BookingProperties;
import com.kunal.flightbooking.domain.*;
import com.kunal.flightbooking.dto.mapper.DtoMapper;
import com.kunal.flightbooking.dto.request.CreateBookingRequest;
import com.kunal.flightbooking.dto.request.PassengerRequest;
import com.kunal.flightbooking.dto.response.BookingResponse;
import com.kunal.flightbooking.exception.BusinessException;
import com.kunal.flightbooking.exception.ResourceNotFoundException;
import com.kunal.flightbooking.exception.SeatUnavailableException;
import com.kunal.flightbooking.pattern.factory.PaymentGatewayFactory;
import com.kunal.flightbooking.pattern.observer.BookingEvent;
import com.kunal.flightbooking.pattern.strategy.FareCalculator;
import com.kunal.flightbooking.pattern.strategy.FareContext;
import com.kunal.flightbooking.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Booking lifecycle: hold seats (create), cancel/refund, and expire stale holds. Payment lives in
 * {@link PaymentService}. Every status change goes through {@link Booking#transitionTo}.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BookingService {

    private static final char[] PNR_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789".toCharArray();
    private static final SecureRandom RANDOM = new SecureRandom();

    private final BookingRepository bookingRepository;
    private final SeatRepository seatRepository;
    private final FlightScheduleRepository scheduleRepository;
    private final UserRepository userRepository;
    private final PaymentRepository paymentRepository;
    private final FareCalculator fareCalculator;
    private final PaymentGatewayFactory gatewayFactory;
    private final OutboxWriter events;
    private final BookingProperties properties;
    private final Clock clock;

    /**
     * Creates a booking and holds the requested seats for {@code app.booking.hold-minutes}.
     * Seats are read with SELECT ... FOR UPDATE, so concurrent requests for the same seat serialise
     * on the row lock: the first commits HELD, the rest see a non-AVAILABLE seat and fail fast.
     */
    @Transactional
    public BookingResponse createBooking(Long userId, CreateBookingRequest req) {
        LocalDateTime now = LocalDateTime.now(clock);
        FlightSchedule schedule = scheduleRepository.findDetailedById(req.scheduleId())
                .orElseThrow(() -> new ResourceNotFoundException("Schedule", req.scheduleId()));
        if (!schedule.isBookable(now)) {
            throw BusinessException.conflict("SCHEDULE_NOT_BOOKABLE", "This flight is no longer open for booking");
        }
        if (req.passengers().size() > properties.maxPassengers()) {
            throw BusinessException.badRequest("TOO_MANY_PASSENGERS",
                    "At most " + properties.maxPassengers() + " passengers per booking");
        }
        List<Long> seatIds = req.passengers().stream().map(PassengerRequest::seatId).toList();
        if (new HashSet<>(seatIds).size() != seatIds.size()) {
            throw BusinessException.badRequest("DUPLICATE_SEAT", "Each passenger needs a different seat");
        }
        User user = userRepository.findById(userId).orElseThrow(() -> new ResourceNotFoundException("User", userId));

        // ---- critical section: row locks held until commit ----
        List<Seat> lockedSeats = seatRepository.findAllByIdForUpdate(seatIds);
        if (lockedSeats.size() != seatIds.size()) {
            throw new ResourceNotFoundException("Seat", seatIds);
        }
        Map<Long, Seat> seatsById = lockedSeats.stream().collect(Collectors.toMap(Seat::getId, Function.identity()));
        SeatClass seatClass = lockedSeats.get(0).getSeatClass();
        for (Seat seat : lockedSeats) {
            if (!seat.getSchedule().getId().equals(schedule.getId())) {
                throw BusinessException.badRequest("SEAT_NOT_ON_SCHEDULE",
                        "Seat " + seat.getId() + " does not belong to schedule " + schedule.getId());
            }
            if (seat.getSeatClass() != seatClass) {
                throw BusinessException.badRequest("MIXED_SEAT_CLASS", "All seats must be in the same cabin");
            }
            if (!seat.isAvailable()) {
                throw new SeatUnavailableException(seat.getSeatNumber());
            }
        }

        int available = (int) seatRepository.countByScheduleAndClassAndStatus(schedule.getId(), seatClass,
                SeatStatus.AVAILABLE);
        String coupon = normaliseCoupon(req.couponCode());
        BigDecimal farePerPassenger = fareCalculator.calculate(new FareContext(seatClass,
                schedule.baseFareFor(seatClass), schedule.getFlight().totalSeats(seatClass), available, coupon));

        Booking booking = new Booking(generatePnr(), user, schedule, seatClass);
        booking.setCouponCode(coupon);
        booking.setTotalAmount(farePerPassenger.multiply(BigDecimal.valueOf(seatIds.size())));
        bookingRepository.save(booking);

        LocalDateTime holdUntil = now.plusMinutes(properties.holdMinutes());
        for (PassengerRequest p : req.passengers()) {
            Seat seat = seatsById.get(p.seatId());
            seat.hold(booking.getId(), holdUntil);
            booking.addPassenger(new Passenger(p.fullName().trim(), p.age(), p.gender(), seat, farePerPassenger));
        }
        booking.setHoldExpiresAt(holdUntil);
        booking.transitionTo(BookingStatus.SEATS_HELD);

        log.info("Booking {} holds seats {} until {}", booking.getPnr(), seatIds, holdUntil);
        events.append(BookingEvent.of(booking));
        return DtoMapper.toResponse(booking);
    }

    @Transactional(readOnly = true)
    public BookingResponse getBooking(Long bookingId, Long userId) {
        return DtoMapper.toResponse(loadOwned(bookingRepository.findById(bookingId), bookingId, userId));
    }

    @Transactional(readOnly = true)
    public List<BookingResponse> listBookings(Long userId) {
        return bookingRepository.findByUserId(userId).stream().map(DtoMapper::toResponse).toList();
    }

    /**
     * SEATS_HELD -> CANCELLED (seats released), or CONFIRMED -> CANCELLED -> REFUNDED (seats released and
     * the captured payment refunded through the same gateway).
     */
    @Transactional
    public BookingResponse cancelBooking(Long bookingId, Long userId) {
        Booking booking = loadOwned(bookingRepository.findByIdForUpdate(bookingId), bookingId, userId);
        LocalDateTime now = LocalDateTime.now(clock);
        if (!booking.getSchedule().getDepartureTime().isAfter(now)) {
            throw BusinessException.conflict("CANCELLATION_CLOSED", "Flight has already departed");
        }
        BookingStatus previous = booking.getStatus();
        booking.transitionTo(BookingStatus.CANCELLED);
        releaseSeats(booking);

        if (previous == BookingStatus.CONFIRMED) {
            Payment payment = paymentRepository.findFirstByBookingIdAndStatus(booking.getId(), PaymentStatus.SUCCESS)
                    .orElseThrow(() -> new IllegalStateException("Confirmed booking without successful payment"));
            var refund = gatewayFactory.get(payment.getMethod())
                    .refund(payment.getGatewayReference(), payment.getAmount());
            if (refund.success()) {
                payment.markRefunded();
                booking.transitionTo(BookingStatus.REFUNDED);
            } else {
                log.error("Refund failed for {}: {}", booking.getPnr(), refund.failureReason());
            }
        }
        log.info("Booking {} cancelled (was {}), now {}", booking.getPnr(), previous, booking.getStatus());
        events.append(BookingEvent.of(booking));
        return DtoMapper.toResponse(booking);
    }

    @Transactional(readOnly = true)
    public List<Long> findExpiredHoldIds() {
        return bookingRepository.findIdsWithExpiredHold(BookingStatus.SEATS_HELD, LocalDateTime.now(clock));
    }

    /**
     * Expires one hold in its own transaction (called per booking by the scheduler, so one failure does
     * not roll back the whole batch). Re-checks state under the lock: a payment may have won the race.
     */
    @Transactional
    public boolean expireHold(Long bookingId) {
        Booking booking = bookingRepository.findByIdForUpdate(bookingId).orElse(null);
        if (booking == null || !booking.isHoldExpired(LocalDateTime.now(clock))) {
            return false;
        }
        releaseSeats(booking);
        booking.setFailureReason("HOLD_EXPIRED");
        booking.transitionTo(BookingStatus.FAILED);
        log.info("Hold expired for booking {}, seats released", booking.getPnr());
        events.append(BookingEvent.of(booking));
        return true;
    }

    void releaseSeats(Booking booking) {
        seatRepository.findByBookingIdForUpdate(booking.getId()).forEach(Seat::release);
        booking.setHoldExpiresAt(null);
    }

    static Booking loadOwned(java.util.Optional<Booking> found, Long bookingId, Long userId) {
        Booking booking = found.orElseThrow(() -> new ResourceNotFoundException("Booking", bookingId));
        if (!booking.isOwnedBy(userId)) {
            // 404 rather than 403: don't reveal that someone else's booking id exists.
            throw new ResourceNotFoundException("Booking", bookingId);
        }
        return booking;
    }

    private static String normaliseCoupon(String coupon) {
        return coupon == null || coupon.isBlank() ? null : coupon.trim().toUpperCase(Locale.ROOT);
    }

    private static String generatePnr() {
        char[] pnr = new char[6];
        for (int i = 0; i < pnr.length; i++) {
            pnr[i] = PNR_ALPHABET[RANDOM.nextInt(PNR_ALPHABET.length)];
        }
        return new String(pnr);
    }
}
