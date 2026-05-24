package com.cpayment.payment.domain.usecase;

import com.cpayment.custody.domain.model.AccountId;
import com.cpayment.custody.domain.model.AssetId;
import com.cpayment.custody.domain.model.NetworkId;
import com.cpayment.payment.domain.exception.InvoiceNotFoundException;
import com.cpayment.payment.domain.model.Invoice;
import com.cpayment.payment.domain.model.InvoiceId;
import com.cpayment.payment.domain.model.MerchantId;
import com.cpayment.payment.domain.port.InvoiceRepository;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class FindInvoiceUseCaseTest {

    private final InvoiceRepository repo = mock(InvoiceRepository.class);
    private final FindInvoiceUseCase useCase = new FindInvoiceUseCase(repo);

    @Test
    void returns_invoice_when_present() {
        Invoice invoice = sample();
        when(repo.findById(invoice.id())).thenReturn(Optional.of(invoice));

        Invoice found = useCase.byId(invoice.id());

        assertThat(found).isEqualTo(invoice);
    }

    @Test
    void throws_not_found_when_missing() {
        InvoiceId id = InvoiceId.newId();
        when(repo.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.byId(id))
            .isInstanceOf(InvoiceNotFoundException.class)
            .hasMessageContaining(id.value().toString());
    }

    private Invoice sample() {
        return Invoice.newlyCreated(
            InvoiceId.newId(),
            MerchantId.of(UUID.randomUUID()),
            new AssetId(new NetworkId("eth", "mainnet"), "usdc"),
            BigInteger.valueOf(1_000_000),
            12,
            AccountId.of(UUID.randomUUID()),
            "0xADDR",
            Instant.parse("2026-05-25T12:00:00Z")
        );
    }
}
