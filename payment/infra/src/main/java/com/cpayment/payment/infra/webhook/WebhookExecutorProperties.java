package com.cpayment.payment.infra.webhook;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "cpayment.webhook.executor")
public record WebhookExecutorProperties(
    Integer coreSize,
    Integer maxSize,
    Integer queueCapacity
) {

    public int effectiveCoreSize()      { return coreSize      != null ? coreSize      : 8;   }
    public int effectiveMaxSize()       { return maxSize       != null ? maxSize       : 16;  }
    public int effectiveQueueCapacity() { return queueCapacity != null ? queueCapacity : 256; }
}
