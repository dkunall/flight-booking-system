package com.kunal.flightbooking.pattern.factory;

import com.kunal.flightbooking.domain.PaymentMethod;
import com.kunal.flightbooking.exception.BusinessException;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Factory that resolves the gateway for a payment method. Gateways self-register via Spring's list
 * injection, so supporting NETBANKING means adding one class and no switch statements.
 */
@Component
public class PaymentGatewayFactory {

    private final Map<PaymentMethod, PaymentGateway> gateways = new EnumMap<>(PaymentMethod.class);

    public PaymentGatewayFactory(List<PaymentGateway> gatewayList) {
        for (PaymentGateway gateway : gatewayList) {
            if (gateways.put(gateway.method(), gateway) != null) {
                throw new IllegalStateException("Duplicate gateway for " + gateway.method());
            }
        }
    }

    public PaymentGateway get(PaymentMethod method) {
        PaymentGateway gateway = gateways.get(method);
        if (gateway == null) {
            throw BusinessException.badRequest("UNSUPPORTED_PAYMENT_METHOD", "Unsupported payment method: " + method);
        }
        return gateway;
    }
}
