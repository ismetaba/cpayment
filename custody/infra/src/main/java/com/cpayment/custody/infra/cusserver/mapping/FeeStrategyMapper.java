package com.cpayment.custody.infra.cusserver.mapping;

import com.cpayment.custody.domain.model.FeePreference;
import org.springframework.stereotype.Component;

/**
 * Translates cpayment's 3-tier FeePreference (Economy/Normal/Fast) into
 * cus-server's polymorphic FeeStrategy (LEVELED/RATED/DIRECT/GAS_BASED/PRIORITY_TIP/DEFAULT).
 *
 * Adapter-internal — never exposed to domain.
 */
@Component
public class FeeStrategyMapper {

    /** Placeholder: returns a string token until OpenAPI-generated FeeStrategy types land. */
    public String toCusFeeStrategy(FeePreference pref) {
        return switch (pref) {
            case FeePreference.Economy e -> "LEVELED:LOW";
            case FeePreference.Normal n  -> "LEVELED:MEDIUM";
            case FeePreference.Fast f    -> "LEVELED:HIGH";
            case FeePreference.Custom c  -> "PRIORITY_TIP:" + c.maxFeePerUnit() + ":" + c.priorityTip();
        };
    }
}
