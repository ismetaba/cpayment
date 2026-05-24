package com.cpayment.custody.domain.port;

import com.cpayment.custody.domain.model.EstimateFeeQuery;
import com.cpayment.custody.domain.model.FeeQuote;

public interface FeePort {
    FeeQuote estimateFee(EstimateFeeQuery query);
}
