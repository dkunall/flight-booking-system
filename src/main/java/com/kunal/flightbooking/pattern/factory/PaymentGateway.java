package com.kunal.flightbooking.pattern.factory;

import com.kunal.flightbooking.domain.PaymentMethod;

import java.math.BigDecimal;

/** Abstraction over an external payment provider. Implementations here are mocks. */
public interface PaymentGateway {

    PaymentMethod method();

    /** Validate method-specific input before any state changes; throws BusinessException (400). */
    void validate(ChargeRequest request);

    GatewayResponse charge(ChargeRequest request);

    GatewayResponse refund(String gatewayReference, BigDecimal amount);

    record ChargeRequest(String pnr, BigDecimal amount, String upiId, String cardToken) {
    }

    record GatewayResponse(boolean success, String reference, String failureReason) {

        public static GatewayResponse ok(String reference) {
            return new GatewayResponse(true, reference, null);
        }

        public static GatewayResponse declined(String reason) {
            return new GatewayResponse(false, null, reason);
        }
    }
}
