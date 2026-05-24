package com.cpayment.payment.infra.config;

import com.cpayment.custody.domain.port.AccountPort;
import com.cpayment.payment.domain.port.InvoiceIdempotencyStore;
import com.cpayment.payment.domain.port.InvoiceRepository;
import com.cpayment.payment.domain.port.MerchantWalletResolver;
import com.cpayment.payment.domain.port.PaymentMetrics;
import com.cpayment.payment.domain.usecase.CreateInvoiceUseCase;
import com.cpayment.payment.domain.usecase.RecordDepositUseCase;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration
@EnableConfigurationProperties(MerchantWalletProperties.class)
@ComponentScan(basePackages = "com.cpayment.payment.infra")
public class PaymentAutoConfiguration {

    @Bean
    public Clock systemClock() {
        return Clock.systemUTC();
    }

    @Bean
    public CreateInvoiceUseCase createInvoiceUseCase(InvoiceRepository invoices,
                                                     InvoiceIdempotencyStore idempotency,
                                                     MerchantWalletResolver wallets,
                                                     AccountPort custodyAccounts,
                                                     PaymentMetrics metrics,
                                                     Clock clock) {
        return new CreateInvoiceUseCase(invoices, idempotency, wallets, custodyAccounts, metrics, clock);
    }

    @Bean
    public RecordDepositUseCase recordDepositUseCase(InvoiceRepository invoices,
                                                     PaymentMetrics metrics,
                                                     Clock clock) {
        return new RecordDepositUseCase(invoices, metrics, clock);
    }
}
