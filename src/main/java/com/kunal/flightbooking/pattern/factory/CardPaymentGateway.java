package com.kunal.flightbooking.pattern.factory;

import com.kunal.flightbooking.domain.PaymentMethod;
import com.kunal.flightbooking.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Mock card provider. Accepts only a tokenised card reference (never a raw PAN), mirroring how real
 * integrations keep card data out of scope. The token "tok_fail" simulates a decline.
 */
@Slf4j
@Component
public class CardPaymentGateway implements PaymentGateway {

    @Override
    public PaymentMethod method() {
        return PaymentMethod.CARD;
    }

    @Override
    public void validate(ChargeRequest request) {
        if (request.cardToken() == null || !request.cardToken().startsWith("tok_")) {
            throw BusinessException.badRequest("INVALID_CARD_TOKEN", "A tokenised cardToken (tok_...) is required");
        }
    }

    @Override
    public GatewayResponse charge(ChargeRequest request) {
        log.info("[MOCK-CARD] authorise+capture INR {} for PNR {}", request.amount(), request.pnr());
        if ("tok_fail".equals(request.cardToken())) {
            return GatewayResponse.declined("CARD_DECLINED");
        }
        return GatewayResponse.ok("CARD-" + UUID.randomUUID());
    }

    @Override
    public GatewayResponse refund(String gatewayReference, BigDecimal amount) {
        log.info("[MOCK-CARD] refund INR {} against {}", amount, gatewayReference);
        return GatewayResponse.ok(gatewayReference);
    }
}
