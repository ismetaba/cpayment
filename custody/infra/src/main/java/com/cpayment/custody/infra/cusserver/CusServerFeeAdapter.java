package com.cpayment.custody.infra.cusserver;

import com.cpayment.custody.domain.model.EstimateFeeQuery;
import com.cpayment.custody.domain.model.FeeQuote;
import com.cpayment.custody.domain.port.FeePort;
import com.cpayment.custody.infra.cusserver.mapping.AssetIdMapper;
import com.cpayment.custody.infra.cusserver.mapping.FeeStrategyMapper;
import com.cpayment.custody.infra.cusserver.rest.CusServerRestClient;
import org.springframework.stereotype.Component;

@Component
public class CusServerFeeAdapter implements FeePort {

    private final CusServerRestClient client;
    private final AssetIdMapper assetMapper;
    private final FeeStrategyMapper feeMapper;

    public CusServerFeeAdapter(CusServerRestClient client, AssetIdMapper assetMapper, FeeStrategyMapper feeMapper) {
        this.client = client;
        this.assetMapper = assetMapper;
        this.feeMapper = feeMapper;
    }

    @Override
    public FeeQuote estimateFee(EstimateFeeQuery query) {
        // TODO POST /api/v1/public/bc/fee/{networkName}/{assetName} with FeeRequestDTO
        throw new UnsupportedOperationException("not yet implemented");
    }
}
