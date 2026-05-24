package com.cpayment.payment.infra.webhook;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

/**
 * Per-merchant webhook delivery settings + scheduler tuning.
 *
 * <pre>
 * cpayment.webhook:
 *   poll-interval:  PT5S
 *   batch-size:     25
 *   max-attempts:   8
 *   max-backoff:    PT1H
 *   connect-timeout: PT3S
 *   read-timeout:    PT10S
 *   merchants:
 *     - merchant-id: 11111111-...
 *       url:    https://merchant.example.com/cpayment/webhook
 *       secret: super-secret-shared-with-merchant
 * </pre>
 */
@ConfigurationProperties(prefix = "cpayment.webhook")
public record MerchantWebhookProperties(
    Duration pollInterval,
    Integer batchSize,
    Integer maxAttempts,
    Duration maxBackoff,
    Duration connectTimeout,
    Duration readTimeout,
    List<Entry> merchants
) {

    public record Entry(UUID merchantId, String url, String secret) {}

    public Duration effectivePollInterval()  { return pollInterval  != null ? pollInterval  : Duration.ofSeconds(5); }
    public int      effectiveBatchSize()     { return batchSize     != null ? batchSize     : 25; }
    public int      effectiveMaxAttempts()   { return maxAttempts   != null ? maxAttempts   : 8; }
    public Duration effectiveMaxBackoff()    { return maxBackoff    != null ? maxBackoff    : Duration.ofHours(1); }
    public Duration effectiveConnectTimeout(){ return connectTimeout!= null ? connectTimeout: Duration.ofSeconds(3); }
    public Duration effectiveReadTimeout()   { return readTimeout   != null ? readTimeout   : Duration.ofSeconds(10); }
    public List<Entry> effectiveMerchants()  { return merchants     != null ? merchants     : List.of(); }
}
