package com.kunal.flightbooking.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kunal.flightbooking.dto.request.PaymentCallbackRequest;
import com.kunal.flightbooking.exception.BusinessException;
import com.kunal.flightbooking.security.WebhookSignatureVerifier;
import com.kunal.flightbooking.service.PaymentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validator;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.util.Map;

/**
 * Server-to-server webhook from the payment gateway. No JWT here; the HMAC signature is the auth, so the
 * body is read raw and verified before it is parsed.
 */
@Tag(name = "Bookings & Payments")
@RestController
@RequiredArgsConstructor
public class PaymentCallbackController {

    public static final String SIGNATURE_HEADER = "X-Signature";

    private final WebhookSignatureVerifier verifier;
    private final PaymentService paymentService;
    private final ObjectMapper objectMapper;
    private final Validator validator;
    private final MeterRegistry meters;

    @Operation(summary = "Gateway callback for async payments (HMAC-SHA256 signed)")
    @PostMapping(path = "/api/payments/callback", consumes = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, String> callback(@RequestHeader(value = SIGNATURE_HEADER, required = false) String signature,
                                        @RequestBody byte[] body) {
        if (!verifier.isValid(body, signature)) {
            meters.counter("payment.callbacks", "outcome", "INVALID_SIGNATURE").increment();
            throw new BusinessException(HttpStatus.UNAUTHORIZED, "INVALID_SIGNATURE",
                    "Callback signature does not match");
        }
        PaymentCallbackRequest request;
        try {
            request = objectMapper.readValue(body, PaymentCallbackRequest.class);
        } catch (IOException e) {
            throw BusinessException.badRequest("BAD_REQUEST", "Malformed callback body");
        }
        var violations = validator.validate(request);
        if (!violations.isEmpty()) {
            throw new ConstraintViolationException(violations);
        }
        var outcome = paymentService.handleCallback(request.gatewayReference(), "SUCCESS".equals(request.status()),
                request.failureReason());
        meters.counter("payment.callbacks", "outcome", outcome.name()).increment();
        return Map.of("result", outcome.name());
    }
}
