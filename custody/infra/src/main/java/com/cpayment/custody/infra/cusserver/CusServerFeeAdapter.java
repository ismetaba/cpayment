package com.cpayment.custody.infra.cusserver;

import com.cpayment.custody.domain.model.AssetId;
import com.cpayment.custody.domain.model.EstimateFeeQuery;
import com.cpayment.custody.domain.model.FeePreference;
import com.cpayment.custody.domain.model.FeeQuote;
import com.cpayment.custody.domain.port.FeePort;
import com.cpayment.custody.infra.cusserver.exception.CustodyAdapterException;
import com.cpayment.custody.infra.cusserver.mapping.AssetIdMapper;
import com.cpayment.custody.infra.cusserver.mapping.NetworkIdMapper;
import com.cpayment.custody.infra.cusserver.rest.CusServerRestClient;
import com.cpayment.custody.infra.cusserver.rest.ResilientHttpExecutor;
import com.cpayment.custody.infra.cusserver.rest.dto.CusResponse;
import com.cpayment.custody.infra.cusserver.rest.dto.FeeRequestDTO;
import com.cpayment.custody.infra.cusserver.rest.dto.FeeResponseDTO;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientResponseException;

import java.math.BigInteger;

/**
 * Real-HTTP fee estimator. Hits cus-server's
 * {@code POST /public/bc/fee/{network}/{asset}} which is public (no auth) and
 * idempotent — retry-safe via {@code resilient.get()}.
 */
@Component
public class CusServerFeeAdapter implements FeePort {

    private final CusServerRestClient client;
    private final NetworkIdMapper networkMapper;
    private final AssetIdMapper assetMapper;
    private final ResilientHttpExecutor resilient;

    public CusServerFeeAdapter(CusServerRestClient client,
                               NetworkIdMapper networkMapper,
                               AssetIdMapper assetMapper,
                               ResilientHttpExecutor resilient) {
        this.client = client;
        this.networkMapper = networkMapper;
        this.assetMapper = assetMapper;
        this.resilient = resilient;
    }

    @Override
    public FeeQuote estimateFee(EstimateFeeQuery query) {
        String network = networkMapper.toCusServer(query.asset().network());
        String asset = assetMapper.toCusServer(query.asset()).assetName();

        FeeRequestDTO body = new FeeRequestDTO(null, null, query.amount());
        String path = "/api/v1/public/bc/fee/" + network + "/" + asset;

        CusResponse<FeeResponseDTO> response = resilient.get(
            "estimateFee",
            () -> {
                try {
                    return client.http().post().uri(path).body(body).retrieve()
                        .body(new ParameterizedTypeReference<CusResponse<FeeResponseDTO>>() {});
                } catch (RestClientResponseException ex) {
                    throw new CustodyAdapterException(
                        "estimateFee failed: " + ex.getStatusCode() + " "
                            + ex.getResponseBodyAsString(), ex);
                }
            });

        if (response == null || response.data() == null) {
            throw new CustodyAdapterException("estimateFee: empty data");
        }
        FeeResponseDTO data = response.data();
        AssetId feeAsset = data.feeAsset() != null
            ? assetMapper.fromCusServer(network, data.feeAsset())
            : query.asset();
        BigInteger fee = data.estimatedFee() != null ? data.estimatedFee() : BigInteger.ZERO;
        return new FeeQuote(feeAsset, fee, query.preference() != null ? query.preference() : FeePreference.NORMAL);
    }
}
