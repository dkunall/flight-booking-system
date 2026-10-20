package com.kunal.flightbooking.pattern.factory;

import com.kunal.flightbooking.domain.PaymentMethod;
import com.kunal.flightbooking.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.regex.Pattern;

/** Mock UPI provider. A VPA whose handle starts with "fail" (e.g. fail@upi) simulates a decline. */
@Slf4j
@Component
public class UpiPaymentGateway implements PaymentGateway {

    private static final Pattern VPA = Pattern.compile("^[a-zA-Z0-9._-]{2,64}@[a-zA-Z]{2,32}$");

    @Override
    public PaymentMethod method() {
        return PaymentMethod.UPI;
    }

    @Override
    public void validate(ChargeRequest request) {
        if (request.upiId() == null || !VPA.matcher(request.upiId()).matches()) {
            throw BusinessException.badRequest("INVALID_UPI_ID", "A valid upiId (e.g. name@okbank) is required");
        }
    }

    @Override
    public GatewayResponse charge(ChargeRequest request) {
        log.info("[MOCK-UPI] collect request of INR {} for PNR {}", request.amount(), request.pnr());
        if (request.upiId().toLowerCase().startsWith("fail")) {
            return GatewayResponse.declined("UPI_DECLINED");
        }
        return GatewayResponse.ok("UPI-" + UUID.randomUUID());
    }

    @Override
    public GatewayResponse refund(String gatewayReference, BigDecimal amount) {
        log.info("[MOCK-UPI] refund INR {} against {}", amount, gatewayReference);
        return GatewayResponse.ok(gatewayReference);
    }
}
