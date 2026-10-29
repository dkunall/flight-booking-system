package com.kunal.flightbooking.unit;

import com.kunal.flightbooking.config.PaymentProperties;
import com.kunal.flightbooking.security.WebhookSignatureVerifier;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class WebhookSignatureVerifierTest {

    private final WebhookSignatureVerifier verifier =
            new WebhookSignatureVerifier(new PaymentProperties("unit-test-secret-unit-test-secret-0000"));
    private final byte[] body = "{\"gatewayReference\":\"NB-1\",\"status\":\"SUCCESS\"}".getBytes(StandardCharsets.UTF_8);

    @Test
    void acceptsItsOwnSignatureInAnyCase() {
        String sig = verifier.sign(body);
        assertThat(sig).hasSize(64).matches("[0-9a-f]+");
        assertThat(verifier.isValid(body, sig)).isTrue();
        assertThat(verifier.isValid(body, sig.toUpperCase())).isTrue();
    }

    @Test
    void rejectsTamperedBodyWrongKeyAndBlankHeader() {
        String sig = verifier.sign(body);
        byte[] tampered = "{\"gatewayReference\":\"NB-1\",\"status\":\"FAILED\"}".getBytes(StandardCharsets.UTF_8);
        var otherKey = new WebhookSignatureVerifier(new PaymentProperties("another-secret-another-secret-00000"));
        assertThat(verifier.isValid(tampered, sig)).isFalse();
        assertThat(verifier.isValid(body, otherKey.sign(body))).isFalse();
        assertThat(verifier.isValid(body, null)).isFalse();
        assertThat(verifier.isValid(body, " ")).isFalse();
    }
}
