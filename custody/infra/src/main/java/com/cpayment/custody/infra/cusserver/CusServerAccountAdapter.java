package com.cpayment.custody.infra.cusserver;

import com.cpayment.custody.domain.model.Account;
import com.cpayment.custody.domain.model.AccountId;
import com.cpayment.custody.domain.model.AssetId;
import com.cpayment.custody.domain.model.CreateAccountCommand;
import com.cpayment.custody.domain.model.NetworkId;
import com.cpayment.custody.domain.model.WalletId;
import com.cpayment.custody.domain.port.AccountPort;
import com.cpayment.custody.infra.cusserver.exception.CustodyAdapterException;
import com.cpayment.custody.infra.cusserver.mapping.AssetIdMapper;
import com.cpayment.custody.infra.cusserver.mapping.NetworkIdMapper;
import com.cpayment.custody.infra.cusserver.rest.CusServerRestClient;
import com.cpayment.custody.infra.cusserver.rest.dto.AccountResponse;
import com.cpayment.custody.infra.cusserver.rest.dto.AddAccountRequest;
import com.cpayment.custody.infra.cusserver.rest.dto.CreateHolderAccountResponse;
import com.cpayment.custody.infra.cusserver.rest.dto.CusResponse;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientResponseException;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Adapts cus-server's {@code /api/v1/holder/accounts} surface to {@link AccountPort}.
 * Each method maps domain command → cus-server DTO, performs the HTTP call, then maps
 * the response back to a domain value object.
 */
@Component
public class CusServerAccountAdapter implements AccountPort {

    private final CusServerRestClient client;
    private final NetworkIdMapper networkMapper;
    private final AssetIdMapper assetMapper;

    public CusServerAccountAdapter(CusServerRestClient client,
                                   NetworkIdMapper networkMapper,
                                   AssetIdMapper assetMapper) {
        this.client = client;
        this.networkMapper = networkMapper;
        this.assetMapper = assetMapper;
    }

    @Override
    public Account createAccount(CreateAccountCommand cmd) {
        AddAccountRequest body = new AddAccountRequest(
            cmd.walletId().value(),
            new AddAccountRequest.Account(
                cmd.label(),
                networkMapper.toCusServer(cmd.network()),
                mapAssetSymbolsToCus(cmd.network(), cmd.supportedAssetSymbols())
            )
        );

        CusResponse<CreateHolderAccountResponse> response = post(
            "/api/v1/holder/accounts", body,
            new ParameterizedTypeReference<>() {}
        );
        CreateHolderAccountResponse data = requireData(response, "createAccount");
        return new Account(
            AccountId.of(data.id()),
            Optional.of(cmd.walletId()),
            cmd.network(),
            data.address(),
            data.label() != null ? data.label() : cmd.label(),
            data.supportedAssets() == null ? Set.of() : Set.copyOf(data.supportedAssets())
        );
    }

    @Override
    public List<Account> listAccountsByWallet(WalletId walletId) {
        CusResponse<PageOfAccount> response;
        try {
            response = client.http().get()
                .uri(uri -> uri.path("/api/v1/holder/accounts").queryParam("walletId", walletId.value()).build())
                .retrieve()
                .body(new ParameterizedTypeReference<>() {});
        } catch (RestClientResponseException ex) {
            throw new CustodyAdapterException(
                "listAccountsByWallet failed: " + ex.getStatusCode() + " " + ex.getResponseBodyAsString(), ex);
        } catch (RuntimeException ex) {
            throw new CustodyAdapterException("listAccountsByWallet failed", ex);
        }

        if (response == null || response.data() == null) return List.of();
        return response.data().content().stream()
            .map(r -> toDomainAccount(r, walletId))
            .toList();
    }

    private Account toDomainAccount(AccountResponse r, WalletId walletContext) {
        NetworkId net = networkMapper.fromCusServer(r.networkName());
        return new Account(
            AccountId.of(r.id()),
            Optional.of(walletContext),
            net,
            r.address(),
            r.label(),
            r.supportedAssets() == null ? Set.of() : Set.copyOf(r.supportedAssets())
        );
    }

    private Set<String> mapAssetSymbolsToCus(NetworkId network, Set<String> symbols) {
        return symbols.stream()
            .map(sym -> assetMapper.toCusServer(new AssetId(network, sym)).assetName())
            .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private <T> CusResponse<T> post(String path, Object body, ParameterizedTypeReference<CusResponse<T>> ref) {
        try {
            return client.http().post().uri(path).body(body).retrieve().body(ref);
        } catch (RestClientResponseException ex) {
            throw new CustodyAdapterException(
                "cus-server POST " + path + " failed: " + ex.getStatusCode() + " " + ex.getResponseBodyAsString(),
                ex);
        } catch (RuntimeException ex) {
            throw new CustodyAdapterException("cus-server POST " + path + " failed", ex);
        }
    }

    private <T> T requireData(CusResponse<T> response, String operation) {
        if (response == null || response.data() == null) {
            throw new CustodyAdapterException(operation + ": cus-server returned empty data");
        }
        return response.data();
    }

    private record PageOfAccount(List<AccountResponse> content) {}
}
