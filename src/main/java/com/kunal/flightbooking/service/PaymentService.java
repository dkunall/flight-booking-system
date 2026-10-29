package com.kunal.flightbooking.service;

import com.kunal.flightbooking.domain.Booking;
import com.kunal.flightbooking.domain.BookingStatus;
import com.kunal.flightbooking.domain.Payment;
import com.kunal.flightbooking.domain.PaymentStatus;
import com.kunal.flightbooking.domain.Seat;
import com.kunal.flightbooking.dto.mapper.DtoMapper;
import com.kunal.flightbooking.dto.request.PaymentRequest;
import com.kunal.flightbooking.dto.response.PaymentResponse;
import com.kunal.flightbooking.exception.HoldExpiredException;
import com.kunal.flightbooking.exception.ResourceNotFoundException;
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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;

/**
 * SEATS_HELD -> PAYMENT_PENDING -> CONFIRMED (seats BOOKED) or FAILED (seats released). UPI and CARD
 * settle inside pay(); NETBANKING stays PAYMENT_PENDING until {@link #handleCallback} runs.
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
    private final OutboxWriter events;
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
        if (result.pending()) {
            // Seats stay held (the hold timer keeps running) until the bank calls back.
            payment.markAwaitingCallback(result.reference());
        } else if (result.success()) {
            payment.markSuccess(result.reference());
            confirm(booking);
        } else {
            payment.markFailed(result.failureReason());
            fail(booking, "PAYMENT_FAILED: " + result.failureReason());
        }
        log.info("Payment {} for {} via {} -> booking {}", payment.getStatus(), booking.getPnr(), req.method(),
                booking.getStatus());
        events.append(BookingEvent.of(booking));
        return DtoMapper.toResponse(payment, booking);
    }

    public enum CallbackOutcome { APPLIED, DUPLICATE, LATE_CAPTURE_REFUNDED }

    /**
     * Applies a verified gateway callback. Safe to call any number of times for the same reference: once
     * the payment has left INITIATED, repeats are no-ops. A success that arrives after the hold expired
     * is refunded straight away, because the seats may already be sold to someone else.
     */
    @Transactional
    public CallbackOutcome handleCallback(String gatewayReference, boolean success, String failureReason) {
        Long bookingId = paymentRepository.findByGatewayReference(gatewayReference)
                .orElseThrow(() -> new ResourceNotFoundException("Payment", gatewayReference))
                .getBooking().getId();
        Booking booking = bookingRepository.findByIdForUpdate(bookingId).orElseThrow();
        Payment payment = paymentRepository.findByGatewayReference(gatewayReference).orElseThrow();

        CallbackOutcome outcome;
        if (payment.getStatus() == PaymentStatus.INITIATED && booking.getStatus() == BookingStatus.PAYMENT_PENDING) {
            if (success) {
                payment.markSuccess(null);
                confirm(booking);
            } else {
                String reason = failureReason == null ? "DECLINED_BY_BANK" : failureReason;
                payment.markFailed(reason);
                fail(booking, "PAYMENT_FAILED: " + reason);
            }
            outcome = CallbackOutcome.APPLIED;
        } else if (success && payment.getStatus() == PaymentStatus.FAILED
                && booking.getStatus() == BookingStatus.FAILED) {
            gatewayFactory.get(payment.getMethod()).refund(gatewayReference, payment.getAmount());
            payment.markLateCaptureRefunded();
            booking.transitionTo(BookingStatus.REFUNDED);
            outcome = CallbackOutcome.LATE_CAPTURE_REFUNDED;
        } else {
            outcome = CallbackOutcome.DUPLICATE;
        }
        log.info("Callback {} for {} ({}): {} -> booking {}", success ? "SUCCESS" : "FAILED", booking.getPnr(),
                gatewayReference, outcome, booking.getStatus());
        if (outcome != CallbackOutcome.DUPLICATE) {
            events.append(BookingEvent.of(booking));
        }
        return outcome;
    }

    private void confirm(Booking booking) {
        seatRepository.findByBookingIdForUpdate(booking.getId()).forEach(seat -> seat.confirm(booking.getId()));
        booking.setHoldExpiresAt(null);
        booking.transitionTo(BookingStatus.CONFIRMED);
    }

    private void fail(Booking booking, String reason) {
        seatRepository.findByBookingIdForUpdate(booking.getId()).forEach(Seat::release);
        booking.setHoldExpiresAt(null);
        booking.setFailureReason(reason);
        booking.transitionTo(BookingStatus.FAILED);
    }
}
