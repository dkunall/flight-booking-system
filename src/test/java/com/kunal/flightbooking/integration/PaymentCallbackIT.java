package com.kunal.flightbooking.integration;

import com.kunal.flightbooking.domain.*;
import com.kunal.flightbooking.dto.request.CreateBookingRequest;
import com.kunal.flightbooking.dto.request.PassengerRequest;
import com.kunal.flightbooking.dto.request.PaymentRequest;
import com.kunal.flightbooking.dto.response.PaymentResponse;
import com.kunal.flightbooking.repository.BookingRepository;
import com.kunal.flightbooking.repository.PaymentRepository;
import com.kunal.flightbooking.repository.SeatRepository;
import com.kunal.flightbooking.security.WebhookSignatureVerifier;
import com.kunal.flightbooking.service.BookingService;
import com.kunal.flightbooking.service.PaymentService;
import com.kunal.flightbooking.service.scheduler.HoldExpiryScheduler;
import com.kunal.flightbooking.support.IntegrationTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.ResultActions;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Netbanking: pay() leaves the booking PAYMENT_PENDING and the signed webhook settles it. */
class PaymentCallbackIT extends IntegrationTestBase {

    @Autowired BookingService bookingService;
    @Autowired PaymentService paymentService;
    @Autowired HoldExpiryScheduler holdExpiry;
    @Autowired WebhookSignatureVerifier signer;
    @Autowired BookingRepository bookingRepository;
    @Autowired PaymentRepository paymentRepository;
    @Autowired SeatRepository seatRepository;
    @Autowired JdbcTemplate jdbc;

    private User user;
    private Long seatId;
    private PaymentResponse pending;

    @BeforeEach
    void startNetbankingPayment() {
        var schedule = data.simpleSchedule();
        seatId = data.seatIds(schedule.id(), SeatClass.ECONOMY).get(0);
        user = data.user(Role.USER);
        var booking = bookingService.createBooking(user.getId(), new CreateBookingRequest(schedule.id(),
                List.of(new PassengerRequest("Net Banker", 35, Gender.FEMALE, seatId)), null));
        pending = paymentService.pay(booking.id(), user.getId(), new PaymentRequest(PaymentMethod.NETBANKING, null, null));
    }

    private ResultActions callback(String body, String signature) throws Exception {
        var request = post("/api/payments/callback").contentType(MediaType.APPLICATION_JSON).content(body);
        if (signature != null) {
            request.header("X-Signature", signature);
        }
        return mvc.perform(request);
    }

    private ResultActions signedCallback(String status) throws Exception {
        String body = """
                {"gatewayReference":"%s","status":"%s"}""".formatted(pending.gatewayReference(), status);
        return callback(body, signer.sign(body.getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void payLeavesBookingPendingWithSeatStillHeld() {
        assertThat(pending.bookingStatus()).isEqualTo(BookingStatus.PAYMENT_PENDING);
        assertThat(pending.status()).isEqualTo(PaymentStatus.INITIATED);
        assertThat(pending.gatewayReference()).startsWith("NB-");
        assertThat(seatRepository.findById(seatId).orElseThrow().getStatus()).isEqualTo(SeatStatus.HELD);
    }

    @Test
    void rejectsMissingOrWrongSignatureWithoutTouchingTheBooking() throws Exception {
        String body = """
                {"gatewayReference":"%s","status":"SUCCESS"}""".formatted(pending.gatewayReference());
        callback(body, null).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_SIGNATURE"));
        callback(body, signer.sign("{\"tampered\":true}".getBytes(StandardCharsets.UTF_8)))
                .andExpect(status().isUnauthorized());
        assertThat(bookingRepository.findById(pending.bookingId()).orElseThrow().getStatus())
                .isEqualTo(BookingStatus.PAYMENT_PENDING);
    }

    @Test
    void successConfirmsOnceAndDuplicatesAreNoOps() throws Exception {
        signedCallback("SUCCESS").andExpect(status().isOk()).andExpect(jsonPath("$.result").value("APPLIED"));
        signedCallback("SUCCESS").andExpect(status().isOk()).andExpect(jsonPath("$.result").value("DUPLICATE"));
        signedCallback("FAILED").andExpect(status().isOk()).andExpect(jsonPath("$.result").value("DUPLICATE"));

        assertThat(bookingRepository.findById(pending.bookingId()).orElseThrow().getStatus())
                .isEqualTo(BookingStatus.CONFIRMED);
        assertThat(paymentRepository.findById(pending.paymentId()).orElseThrow().getStatus())
                .isEqualTo(PaymentStatus.SUCCESS);
        assertThat(seatRepository.findById(seatId).orElseThrow().getStatus()).isEqualTo(SeatStatus.BOOKED);
    }

    @Test
    void failureReleasesTheSeat() throws Exception {
        signedCallback("FAILED").andExpect(jsonPath("$.result").value("APPLIED"));
        assertThat(bookingRepository.findById(pending.bookingId()).orElseThrow().getStatus())
                .isEqualTo(BookingStatus.FAILED);
        assertThat(seatRepository.findById(seatId).orElseThrow().getStatus()).isEqualTo(SeatStatus.AVAILABLE);
    }

    @Test
    void successAfterHoldExpiredIsRefunded() throws Exception {
        jdbc.update("UPDATE bookings SET hold_expires_at = ? WHERE id = ?",
                LocalDateTime.now().minusMinutes(1), pending.bookingId());
        assertThat(holdExpiry.releaseExpiredHolds()).isGreaterThanOrEqualTo(1);
        assertThat(paymentRepository.findById(pending.paymentId()).orElseThrow().getStatus())
                .isEqualTo(PaymentStatus.FAILED);

        signedCallback("SUCCESS").andExpect(jsonPath("$.result").value("LATE_CAPTURE_REFUNDED"));

        assertThat(bookingRepository.findById(pending.bookingId()).orElseThrow().getStatus())
                .isEqualTo(BookingStatus.REFUNDED);
        assertThat(paymentRepository.findById(pending.paymentId()).orElseThrow().getStatus())
                .isEqualTo(PaymentStatus.REFUNDED);
        assertThat(seatRepository.findById(seatId).orElseThrow().getStatus()).isEqualTo(SeatStatus.AVAILABLE);
    }

    @Test
    void unknownReferenceIs404() throws Exception {
        String body = "{\"gatewayReference\":\"NB-nope\",\"status\":\"SUCCESS\"}";
        callback(body, signer.sign(body.getBytes(StandardCharsets.UTF_8))).andExpect(status().isNotFound());
    }
}
