package com.cpayment.payment.infra.webhook;

import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.NoSuchAlgorithmException;
import java.security.InvalidKeyException;
import java.util.HexFormat;

/**
 * HMAC-SHA256 signature over the raw JSON body, formatted as lowercase hex. Sent in
 * the {@code X-Cpayment-Signature} header. Format mirrors what most major payment
 * gateways use (Stripe, Shopify) so merchants can adapt existing client libraries.
 */
@Component
public class WebhookSigner {

    private static final String HMAC_ALG = "HmacSHA256";

    public String sign(String body, String secret) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALG);
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC_ALG));
            byte[] hash = mac.doFinal(body.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("HmacSHA256 unavailable", e);
        } catch (InvalidKeyException e) {
            throw new IllegalArgumentException("invalid HMAC key", e);
        }
    }
}
