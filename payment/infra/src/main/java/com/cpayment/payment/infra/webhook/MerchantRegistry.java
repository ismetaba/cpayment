package com.cpayment.payment.infra.webhook;

import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Resolves a merchant's webhook URL and signing secret. Snapshot loaded from
 * {@link MerchantWebhookProperties} at construction; live changes require a restart.
 * Production would join a {@code merchant_webhook} table indexed by merchant_id.
 */
@Component
public class MerchantRegistry {

    public record WebhookEndpoint(String url, String secret) {}

    private final Map<UUID, WebhookEndpoint> byMerchant;

    public MerchantRegistry(MerchantWebhookProperties props) {
        Map<UUID, WebhookEndpoint> m = new HashMap<>();
        for (MerchantWebhookProperties.Entry e : props.effectiveMerchants()) {
            m.put(e.merchantId(), new WebhookEndpoint(e.url(), e.secret()));
        }
        this.byMerchant = Map.copyOf(m);
    }

    public Optional<WebhookEndpoint> lookup(UUID merchantId) {
        return Optional.ofNullable(byMerchant.get(merchantId));
    }
}
