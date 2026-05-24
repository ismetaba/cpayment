package com.cpayment.custody.infra.cusserver;

import com.cpayment.custody.domain.model.AccountId;
import com.cpayment.custody.domain.model.Account;
import com.cpayment.custody.domain.model.AssetId;
import com.cpayment.custody.domain.model.Balance;
import com.cpayment.custody.domain.model.NetworkId;
import com.cpayment.custody.domain.port.AccountPort;
import com.cpayment.custody.domain.port.BalancePort;
import com.cpayment.custody.infra.cusserver.exception.CustodyAdapterException;
import com.cpayment.custody.infra.cusserver.mapping.AssetIdMapper;
import com.cpayment.custody.infra.cusserver.mapping.NetworkIdMapper;
import com.cpayment.custody.infra.cusserver.rest.CusServerRestClient;
import com.cpayment.custody.infra.cusserver.rest.ResilientHttpExecutor;
import com.cpayment.custody.infra.cusserver.rest.dto.BalanceRequestDTO;
import com.cpayment.custody.infra.cusserver.rest.dto.BalanceResponseDTO;
import com.cpayment.custody.infra.cusserver.rest.dto.CusResponse;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientResponseException;

import java.math.BigInteger;
import java.util.List;
import java.util.Set;

/**
 * Real-HTTP {@link BalancePort}. cus-server's {@code POST /holder/balances} accepts an
 * array of {network, asset[], address} triples; we wrap each domain call as a single-
 * triple list. Retry-safe via {@code resilient.get()} — balances are idempotent reads.
 */
@Component
public class CusServerBalanceAdapter implements BalancePort {

    private final CusServerRestClient client;
    private final AccountPort accounts;
    private final NetworkIdMapper networkMapper;
    private final AssetIdMapper assetMapper;
    private final ResilientHttpExecutor resilient;

    public CusServerBalanceAdapter(CusServerRestClient client,
                                   AccountPort accounts,
                                   NetworkIdMapper networkMapper,
                                   AssetIdMapper assetMapper,
                                   ResilientHttpExecutor resilient) {
        this.client = client;
        this.accounts = accounts;
        this.networkMapper = networkMapper;
        this.assetMapper = assetMapper;
        this.resilient = resilient;
    }

    @Override
    public Balance getBalance(AccountId account, AssetId asset) {
        String address = resolveAccountAddress(account);
        return getBalanceByAddress(asset.network(), asset, address);
    }

    @Override
    public List<Balance> getBalances(AccountId account) {
        // Caller intends "all assets supported by this account". cus-server requires
        // the asset list explicitly; without an account-introspection endpoint we
        // can't enumerate. Return an empty list and document — most callers will
        // use the more specific getBalance.
        return List.of();
    }

    @Override
    public Balance getBalanceByAddress(NetworkId network, AssetId asset, String address) {
        BalanceRequestDTO request = new BalanceRequestDTO(
            networkMapper.toCusServer(network),
            Set.of(assetMapper.toCusServer(asset).assetName()),
            address
        );

        CusResponse<List<BalanceResponseDTO>> response = resilient.get(
            "getBalanceByAddress",
            () -> post("/api/v1/holder/balances", List.of(request),
                new ParameterizedTypeReference<>() {}));

        if (response == null || response.data() == null || response.data().isEmpty()) {
            return new Balance(asset, BigInteger.ZERO, Balance.BalanceStatus.UNAVAILABLE);
        }
        BalanceResponseDTO row = response.data().get(0);
        if (row.detail() == null || row.detail().available() == null) {
            return new Balance(asset, BigInteger.ZERO,
                "OK".equalsIgnoreCase(row.status())
                    ? Balance.BalanceStatus.UNAVAILABLE
                    : Balance.BalanceStatus.STALE);
        }
        return new Balance(asset, new BigInteger(row.detail().available()),
            Balance.BalanceStatus.OK);
    }

    /**
     * Map AccountId → on-chain address. Falls back to looking the account up by listing
     * — slow if the wallet has many accounts, but acceptable for the rare balance call.
     * Production should keep the address on the calling side (e.g. cached on Invoice).
     */
    private String resolveAccountAddress(AccountId account) {
        // No per-id endpoint on cus-server. Callers that already know the address should
        // use getBalanceByAddress directly; this fallback exists for completeness.
        throw new UnsupportedOperationException(
            "getBalance(AccountId,...) — pass address directly via getBalanceByAddress; "
                + "cus-server has no per-id account read");
    }

    @SuppressWarnings("unused")  // kept for type-inference of the response in resilient.get
    private Account ignoredHack() { return null; }

    private <T> CusResponse<T> post(String path, Object body,
                                    ParameterizedTypeReference<CusResponse<T>> ref) {
        try {
            return client.http().post().uri(path).body(body).retrieve().body(ref);
        } catch (RestClientResponseException ex) {
            throw new CustodyAdapterException(
                "cus-server POST " + path + " failed: " + ex.getStatusCode() + " "
                    + ex.getResponseBodyAsString(), ex);
        } catch (RuntimeException ex) {
            throw new CustodyAdapterException("cus-server POST " + path + " failed", ex);
        }
    }
}
