package com.kunal.flightbooking.service;

import com.kunal.flightbooking.domain.Booking;
import com.kunal.flightbooking.domain.BookingStatus;
import com.kunal.flightbooking.domain.Payment;
import com.kunal.flightbooking.domain.Seat;
import com.kunal.flightbooking.dto.mapper.DtoMapper;
import com.kunal.flightbooking.dto.request.PaymentRequest;
import com.kunal.flightbooking.dto.response.PaymentResponse;
import com.kunal.flightbooking.exception.HoldExpiredException;
import com.kunal.flightbooking.pattern.factory.PaymentGateway;
import com.kunal.flightbooking.pattern.factory.PaymentGateway.ChargeRequest;
import com.kunal.flightbooking.pattern.factory.PaymentGateway.GatewayResponse;
import com.kunal.flightbooking.pattern.factory.PaymentGatewayFactory;
import com.kunal.flightbooking.pattern.observer.BookingEvent;
import com.kunal.flightbooking.repository.BookingRepository;
import com.kunal.flightbooking.repository.PaymentRepository;
import com.kunal.flightbooking.repository.SeatRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

/**
 * SEATS_HELD -> PAYMENT_PENDING -> CONFIRMED (seats BOOKED) or FAILED (seats released).
 * The booking row is locked for the whole operation so pay, cancel and hold-expiry cannot interleave.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentService {

    private final BookingRepository bookingRepository;
    private final SeatRepository seatRepository;
    private final PaymentRepository paymentRepository;
    private final PaymentGatewayFactory gatewayFactory;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    @Transactional
    public PaymentResponse pay(Long bookingId, Long userId, PaymentRequest req) {
        Booking booking = BookingService.loadOwned(bookingRepository.findByIdForUpdate(bookingId), bookingId, userId);
        if (booking.isHoldExpired(LocalDateTime.now(clock))) {
            throw new HoldExpiredException(booking.getPnr());
        }
        PaymentGateway gateway = gatewayFactory.get(req.method());
        ChargeRequest charge = new ChargeRequest(booking.getPnr(), booking.getTotalAmount(), req.upiId(),
                req.cardToken());
        gateway.validate(charge);

        booking.transitionTo(BookingStatus.PAYMENT_PENDING);   // throws unless currently SEATS_HELD
        Payment payment = paymentRepository.save(new Payment(booking, req.method(), booking.getTotalAmount()));

        GatewayResponse result = gateway.charge(charge);
        List<Seat> seats = seatRepository.findByBookingIdForUpdate(booking.getId());
        if (result.success()) {
            payment.markSuccess(result.reference());
            seats.forEach(seat -> seat.confirm(booking.getId()));
            booking.setHoldExpiresAt(null);
            booking.transitionTo(BookingStatus.CONFIRMED);
        } else {
            payment.markFailed(result.failureReason());
            seats.forEach(Seat::release);
            booking.setHoldExpiresAt(null);
            booking.setFailureReason("PAYMENT_FAILED: " + result.failureReason());
            booking.transitionTo(BookingStatus.FAILED);
        }
        log.info("Payment {} for {} via {} -> booking {}", payment.getStatus(), booking.getPnr(), req.method(),
                booking.getStatus());
        events.publishEvent(BookingEvent.of(booking));
        return DtoMapper.toResponse(payment, booking);
    }
}
