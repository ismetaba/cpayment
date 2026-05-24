package com.cpayment.payment.domain.usecase;

import com.cpayment.custody.domain.model.AssetId;
import com.cpayment.custody.domain.model.NetworkId;
import com.cpayment.payment.domain.exception.PayoutNotFoundException;
import com.cpayment.payment.domain.model.MerchantId;
import com.cpayment.payment.domain.model.Payout;
import com.cpayment.payment.domain.model.PayoutId;
import com.cpayment.payment.domain.port.PayoutRepository;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class FindPayoutUseCaseTest {

    private final PayoutRepository repo = mock(PayoutRepository.class);
    private final FindPayoutUseCase useCase = new FindPayoutUseCase(repo);

    @Test
    void returns_payout_when_present() {
        Payout payout = sample();
        when(repo.findById(payout.id())).thenReturn(Optional.of(payout));

        assertThat(useCase.byId(payout.id())).isEqualTo(payout);
    }

    @Test
    void throws_not_found_when_missing() {
        PayoutId id = PayoutId.newId();
        when(repo.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.byId(id))
            .isInstanceOf(PayoutNotFoundException.class)
            .hasMessageContaining(id.value().toString());
    }

    private Payout sample() {
        return Payout.requested(
            PayoutId.newId(),
            MerchantId.of(UUID.randomUUID()),
            new AssetId(new NetworkId("eth", "mainnet"), "usdc"),
            "0xFROM", "0xTO",
            BigInteger.valueOf(1_000_000),
            Optional.empty(),
            Instant.parse("2026-05-25T12:00:00Z")
        );
    }
}
