package com.cpayment.payment.infra.config;

import com.cpayment.custody.domain.port.AccountPort;
import com.cpayment.custody.domain.port.TransferPort;
import com.cpayment.payment.domain.port.InvoiceIdempotencyStore;
import com.cpayment.payment.domain.port.InvoiceMutationGateway;
import com.cpayment.payment.domain.port.InvoiceRepository;
import com.cpayment.payment.domain.port.MerchantWalletResolver;
import com.cpayment.payment.domain.port.PaymentMetrics;
import com.cpayment.payment.domain.port.PayoutIdempotencyStore;
import com.cpayment.payment.domain.port.PayoutMutationGateway;
import com.cpayment.payment.domain.port.PayoutRepository;
import com.cpayment.payment.domain.usecase.CreateInvoiceUseCase;
import com.cpayment.payment.domain.usecase.ExecutePayoutUseCase;
import com.cpayment.payment.domain.usecase.RecordDepositUseCase;
import com.cpayment.payment.domain.usecase.UpdatePayoutFromTransferUseCase;
import com.cpayment.payment.infra.webhook.MerchantWebhookProperties;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import java.time.Clock;

@Configuration
@EnableConfigurationProperties({ MerchantWalletProperties.class, MerchantWebhookProperties.class })
@ComponentScan(basePackages = "com.cpayment.payment.infra")
@EntityScan(basePackages = "com.cpayment.payment.infra.persistence.jpa")
@EnableJpaRepositories(basePackages = "com.cpayment.payment.infra.persistence.jpa")
@EnableTransactionManagement
@EnableScheduling
public class PaymentAutoConfiguration {

    @Bean
    public Clock systemClock() {
        return Clock.systemUTC();
    }

    @Bean
    public CreateInvoiceUseCase createInvoiceUseCase(InvoiceMutationGateway gateway,
                                                     InvoiceIdempotencyStore idempotency,
                                                     MerchantWalletResolver wallets,
                                                     AccountPort custodyAccounts,
                                                     PaymentMetrics metrics,
                                                     Clock clock) {
        return new CreateInvoiceUseCase(gateway, idempotency, wallets, custodyAccounts, metrics, clock);
    }

    @Bean
    public RecordDepositUseCase recordDepositUseCase(InvoiceRepository invoices,
                                                     InvoiceMutationGateway gateway,
                                                     PaymentMetrics metrics,
                                                     Clock clock) {
        return new RecordDepositUseCase(invoices, gateway, metrics, clock);
    }

    @Bean
    public ExecutePayoutUseCase executePayoutUseCase(PayoutIdempotencyStore idempotency,
                                                     PayoutMutationGateway gateway,
                                                     TransferPort transfers,
                                                     Clock clock) {
        return new ExecutePayoutUseCase(idempotency, gateway, transfers, clock);
    }

    @Bean
    public UpdatePayoutFromTransferUseCase updatePayoutFromTransferUseCase(PayoutRepository payouts,
                                                                          PayoutMutationGateway gateway,
                                                                          Clock clock) {
        return new UpdatePayoutFromTransferUseCase(payouts, gateway, clock);
    }
}
