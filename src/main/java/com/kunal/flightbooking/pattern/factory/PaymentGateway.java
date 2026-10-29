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

    /**
     * {@code pending} means the customer was redirected to their bank and the result will arrive later
     * on the signed callback ({@code POST /api/payments/callback}).
     */
    record GatewayResponse(boolean success, boolean pending, String reference, String failureReason) {

        public static GatewayResponse ok(String reference) {
            return new GatewayResponse(true, false, reference, null);
        }

        public static GatewayResponse declined(String reason) {
            return new GatewayResponse(false, false, null, reason);
        }

        public static GatewayResponse pending(String reference) {
            return new GatewayResponse(false, true, reference, null);
        }
    }
}
