package com.cpayment.it;

import org.springframework.amqp.core.Queue;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

/**
 * Declares the test queues so the broker has them before listeners attach. Real
 * deployments rely on cus-server to declare these queues; the integration test
 * stands in for that responsibility.
 */
@TestConfiguration
public class RabbitTestConfig {

    @Bean
    public Queue createDepositQueue() {
        return new Queue("cpayment.test.deposits", true, false, false);
    }

    @Bean
    public Queue updateTransactionQueue() {
        return new Queue("cpayment.test.tx-updates", true, false, false);
    }
}
