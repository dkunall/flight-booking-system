package com.kunal.flightbooking.security;

import com.kunal.flightbooking.config.PaymentProperties;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.HexFormat;

/** HMAC-SHA256 over the raw request body, hex encoded. Same scheme Razorpay and Stripe use. */
@Component
public class WebhookSignatureVerifier {

    private static final String ALGORITHM = "HmacSHA256";
    private final SecretKeySpec key;

    public WebhookSignatureVerifier(PaymentProperties properties) {
        this.key = new SecretKeySpec(properties.webhookSecret().getBytes(StandardCharsets.UTF_8), ALGORITHM);
    }

    public String sign(byte[] body) {
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(key);
            return HexFormat.of().formatHex(mac.doFinal(body));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    public boolean isValid(byte[] body, String signatureHeader) {
        if (signatureHeader == null || signatureHeader.isBlank()) {
            return false;
        }
        byte[] expected = sign(body).getBytes(StandardCharsets.US_ASCII);
        byte[] actual = signatureHeader.trim().toLowerCase().getBytes(StandardCharsets.US_ASCII);
        return MessageDigest.isEqual(expected, actual);   // constant time
    }
}
