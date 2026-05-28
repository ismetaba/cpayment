package com.cpayment.payment.infra.webhook;

import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.NoSuchAlgorithmException;
import java.security.InvalidKeyException;
import java.util.HexFormat;

/**
 * HMAC-SHA256 signature for outbound webhooks, formatted as lowercase hex and sent in
 * the {@code X-Cpayment-Signature} header alongside an {@code X-Cpayment-Timestamp}
 * header.
 *
 * <p>The signature covers {@code "{timestamp}.{body}"} (Stripe's scheme) rather than the
 * bare body. Binding the send timestamp into the signed payload lets merchants reject
 * replayed deliveries by checking that the timestamp is recent before trusting the
 * signature — a bare-body signature is replayable forever.
 *
 * <p>Merchant verification: recompute {@code HMAC_SHA256(secret, timestamp + "." + body)}
 * and compare (constant-time) against {@code X-Cpayment-Signature}, after asserting that
 * {@code X-Cpayment-Timestamp} is within an acceptable clock skew.
 */
@Component
public class WebhookSigner {

    private static final String HMAC_ALG = "HmacSHA256";

    /** HMAC over {@code "{timestampSeconds}.{body}"}, returned as lowercase hex. */
    public String sign(long timestampSeconds, String body, String secret) {
        String signedPayload = timestampSeconds + "." + body;
        try {
            Mac mac = Mac.getInstance(HMAC_ALG);
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC_ALG));
            byte[] hash = mac.doFinal(signedPayload.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("HmacSHA256 unavailable", e);
        } catch (InvalidKeyException e) {
            throw new IllegalArgumentException("invalid HMAC key", e);
        }
    }
}
