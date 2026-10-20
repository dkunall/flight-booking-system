package com.kunal.flightbooking.controller;

import com.kunal.flightbooking.dto.request.CreateBookingRequest;
import com.kunal.flightbooking.dto.request.PaymentRequest;
import com.kunal.flightbooking.dto.response.BookingResponse;
import com.kunal.flightbooking.dto.response.PaymentResponse;
import com.kunal.flightbooking.security.AuthUser;
import com.kunal.flightbooking.service.BookingService;
import com.kunal.flightbooking.service.IdempotencyService;
import com.kunal.flightbooking.service.PaymentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "Bookings & Payments")
@RestController
@RequestMapping("/api/bookings")
@RequiredArgsConstructor
public class BookingController {

    public static final String IDEMPOTENCY_HEADER = "Idempotency-Key";
    public static final String REPLAYED_HEADER = "Idempotent-Replayed";

    private final BookingService bookingService;
    private final PaymentService paymentService;
    private final IdempotencyService idempotencyService;

    @Operation(summary = "Create a booking and hold the chosen seats for 10 minutes")
    @PostMapping
    public ResponseEntity<BookingResponse> create(@AuthenticationPrincipal AuthUser user,
                                                  @RequestHeader(IDEMPOTENCY_HEADER) String idempotencyKey,
                                                  @Valid @RequestBody CreateBookingRequest request) {
        var result = idempotencyService.execute(idempotencyKey, user.getId(), "CREATE_BOOKING", request,
                HttpStatus.CREATED.value(), BookingResponse.class,
                () -> bookingService.createBooking(user.getId(), request));
        return ResponseEntity.status(HttpStatus.CREATED)
                .header(REPLAYED_HEADER, String.valueOf(result.replayed()))
                .body(result.body());
    }

    @Operation(summary = "Pay for a held booking (UPI or CARD, mock gateways)")
    @PostMapping("/{bookingId}/payments")
    public ResponseEntity<PaymentResponse> pay(@AuthenticationPrincipal AuthUser user,
                                               @PathVariable Long bookingId,
                                               @RequestHeader(IDEMPOTENCY_HEADER) String idempotencyKey,
                                               @Valid @RequestBody PaymentRequest request) {
        var result = idempotencyService.execute(idempotencyKey, user.getId(), "PAY_BOOKING_" + bookingId, request,
                HttpStatus.OK.value(), PaymentResponse.class,
                () -> paymentService.pay(bookingId, user.getId(), request));
        return ResponseEntity.ok()
                .header(REPLAYED_HEADER, String.valueOf(result.replayed()))
                .body(result.body());
    }

    @Operation(summary = "Cancel a booking (refunds if it was confirmed)")
    @PostMapping("/{bookingId}/cancel")
    public BookingResponse cancel(@AuthenticationPrincipal AuthUser user, @PathVariable Long bookingId) {
        return bookingService.cancelBooking(bookingId, user.getId());
    }

    @Operation(summary = "Get one of my bookings")
    @GetMapping("/{bookingId}")
    public BookingResponse get(@AuthenticationPrincipal AuthUser user, @PathVariable Long bookingId) {
        return bookingService.getBooking(bookingId, user.getId());
    }

    @Operation(summary = "List my bookings, newest first")
    @GetMapping
    public List<BookingResponse> list(@AuthenticationPrincipal AuthUser user) {
        return bookingService.listBookings(user.getId());
    }
}
