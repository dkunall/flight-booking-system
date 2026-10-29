package com.kunal.flightbooking.pattern.factory;

import com.kunal.flightbooking.domain.PaymentMethod;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Mock netbanking: charge() only "redirects" the customer and returns a reference. The outcome
 * arrives later on the signed webhook, like Razorpay/PayU netbanking flows.
 */
@Slf4j
@Component
public class NetbankingPaymentGateway implements PaymentGateway {

    @Override
    public PaymentMethod method() {
        return PaymentMethod.NETBANKING;
    }

    @Override
    public void validate(ChargeRequest request) {
        // nothing to check up front; the bank collects credentials on its own page
    }

    @Override
    public GatewayResponse charge(ChargeRequest request) {
        String reference = "NB-" + UUID.randomUUID();
        log.info("[MOCK-NETBANKING] redirect for INR {} PNR {} ref {}", request.amount(), request.pnr(), reference);
        return GatewayResponse.pending(reference);
    }

    @Override
    public GatewayResponse refund(String gatewayReference, BigDecimal amount) {
        log.info("[MOCK-NETBANKING] refund INR {} against {}", amount, gatewayReference);
        return GatewayResponse.ok(gatewayReference);
    }
}
