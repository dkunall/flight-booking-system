package com.kunal.flightbooking.unit;

import com.kunal.flightbooking.domain.PaymentMethod;
import com.kunal.flightbooking.exception.BusinessException;
import com.kunal.flightbooking.pattern.factory.CardPaymentGateway;
import com.kunal.flightbooking.pattern.factory.PaymentGateway;
import com.kunal.flightbooking.pattern.factory.PaymentGateway.ChargeRequest;
import com.kunal.flightbooking.pattern.factory.PaymentGatewayFactory;
import com.kunal.flightbooking.pattern.factory.UpiPaymentGateway;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PaymentGatewayFactoryTest {

    private final PaymentGatewayFactory factory =
            new PaymentGatewayFactory(List.of(new UpiPaymentGateway(), new CardPaymentGateway()));

    private static ChargeRequest charge(String upi, String card) {
        return new ChargeRequest("ABC123", new BigDecimal("4999.00"), upi, card);
    }

    @Test
    void resolvesGatewayByMethod() {
        assertThat(factory.get(PaymentMethod.UPI)).isInstanceOf(UpiPaymentGateway.class);
        assertThat(factory.get(PaymentMethod.CARD)).isInstanceOf(CardPaymentGateway.class);
    }

    @Test
    void missingGatewayIsReportedAsUnsupported() {
        PaymentGatewayFactory upiOnly = new PaymentGatewayFactory(List.of(new UpiPaymentGateway()));
        assertThatThrownBy(() -> upiOnly.get(PaymentMethod.CARD))
                .isInstanceOf(BusinessException.class).hasMessageContaining("Unsupported");
    }

    @Test
    void duplicateRegistrationFailsFast() {
        assertThatThrownBy(() -> new PaymentGatewayFactory(List.of(new UpiPaymentGateway(), new UpiPaymentGateway())))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void upiChargesSucceedOrDeclineDeterministically() {
        PaymentGateway upi = factory.get(PaymentMethod.UPI);
        assertThat(upi.charge(charge("kunal@okaxis", null)).success()).isTrue();
        assertThat(upi.charge(charge("kunal@okaxis", null)).reference()).startsWith("UPI-");
        var declined = upi.charge(charge("fail@okaxis", null));
        assertThat(declined.success()).isFalse();
        assertThat(declined.failureReason()).isEqualTo("UPI_DECLINED");
    }

    @Test
    void upiValidatesVpa() {
        assertThatThrownBy(() -> factory.get(PaymentMethod.UPI).validate(charge("not-a-vpa", null)))
                .isInstanceOf(BusinessException.class).hasMessageContaining("upiId");
    }

    @Test
    void cardRequiresTokenNotRawNumber() {
        PaymentGateway card = factory.get(PaymentMethod.CARD);
        assertThatThrownBy(() -> card.validate(charge(null, "4111111111111111")))
                .isInstanceOf(BusinessException.class);
        assertThat(card.charge(charge(null, "tok_visa_4242")).success()).isTrue();
        assertThat(card.charge(charge(null, "tok_fail")).success()).isFalse();
    }
}
