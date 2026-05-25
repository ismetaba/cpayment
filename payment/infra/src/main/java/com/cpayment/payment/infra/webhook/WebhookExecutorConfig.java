package com.cpayment.payment.infra.webhook;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/**
 * Dedicated thread pool for webhook delivery tasks. Sized small (8) by default:
 * the dispatcher polls every few seconds so the steady-state load is low; the goal
 * of the pool is to <em>isolate</em> a slow merchant, not to absorb a deluge.
 *
 * <p>Tunable through {@code cpayment.webhook.executor.{core-size,max-size,queue-capacity}}.
 * Defaults are deliberately small so a misconfigured deployment can't accidentally
 * spawn hundreds of threads.
 */
@Configuration
public class WebhookExecutorConfig {

    @Bean(name = "webhookDeliveryExecutor", destroyMethod = "shutdown")
    public Executor webhookDeliveryExecutor(WebhookExecutorProperties props) {
        ThreadPoolTaskExecutor exec = new ThreadPoolTaskExecutor();
        exec.setCorePoolSize(props.effectiveCoreSize());
        exec.setMaxPoolSize(props.effectiveMaxSize());
        exec.setQueueCapacity(props.effectiveQueueCapacity());
        exec.setThreadNamePrefix("webhook-");
        exec.setWaitForTasksToCompleteOnShutdown(true);
        exec.setAwaitTerminationSeconds(15);
        exec.initialize();
        return exec;
    }
}
