# cpayment

Payments product that talks to custody only through a provider-agnostic adapter.
First adapter: **TÜBİTAK BİLGEM cus-server** (REST + RabbitMQ + Keycloak).

## Architecture

```
core            value objects + base exceptions (IdempotencyKey, Money, …)
custody/
  domain        segregated ports, sealed CustodyEvent, Capabilities — zero framework
  infra         cus-server adapter (REST + OAuth2 + RabbitMQ bridge), Resilience4j
payment/
  domain        Invoice aggregate, CreateInvoice + RecordDeposit use cases — zero framework
  infra         REST controller, in-memory stores, custody event dispatcher
dist            Spring Boot bootable
```

Hexagonal boundaries are enforced at compile time by ArchUnit
(`HexagonalBoundaryTest`): no `*.domain` package may import Spring, Servlet,
Jackson, or Spring AMQP.

## Production-hardened features

- **Idempotency** on `POST /invoices` via mandatory `Idempotency-Key` header;
  same key + body returns the cached invoice without re-calling cus-server.
- **HTTP timeouts** (connect/read) on every outbound call, configurable.
- **Resilience4j**: retry on idempotent GETs only (POSTs would create duplicate
  custody accounts), circuit breaker on everything.
- **RabbitMQ Jackson converter** with a trusted-packages allowlist on the
  type mapper.
- **OAuth2 client-credentials** to Keycloak, cached with refresh skew.
- **Observability**: Micrometer counters tagged by asset, `X-Correlation-Id`
  propagated through MDC for both HTTP and RabbitMQ flows, JSON logs via
  logstash-logback-encoder (`-Dspring.profiles.active=json`), Actuator at
  `/actuator/{health,info,metrics,prometheus}`.

## Building

```bash
mvn test          # unit + ArchUnit, ~4s, no external deps
mvn verify        # adds Failsafe integration test — requires Docker
mvn package       # produces dist/target/cpayment.jar
```

## Running locally

Requires:

- RabbitMQ on `localhost:5672` (defaults to `custody-admin/admin.1234`)
- Keycloak realm at `http://localhost:8090/realms/tubitak`
- cus-server REST at `http://localhost:1616`

All four overridable through env vars (`CUS_BASE_URL`, `CUS_RABBIT_HOST`,
`CUS_ISSUER`, …). See `dist/src/main/resources/application.yml`.

```bash
java -jar dist/target/cpayment.jar
```

## Endpoints

```
POST  /api/v1/invoices       Idempotency-Key required → 201 + Location
GET   /api/v1/invoices/{id}                            → 200 | 404
POST  /api/v1/payouts                Idempotency-Key required → 201 + Location
GET   /api/v1/payouts/{id}                                    → 200 | 404
POST  /api/v1/payouts/{id}/cancel                             → 200 | 404 | 422
GET   /actuator/{health,info,metrics,prometheus}
GET   /swagger-ui.html       interactive OpenAPI 3 UI
GET   /v3/api-docs           raw OpenAPI 3 spec (merchant API only)
```

`POST /api/v1/invoices` with header `Idempotency-Key: <unique>` and body:

```json
{ "merchantId": "...", "asset": "eth:mainnet:usdc", "expectedAmount": 1000000 }
```

`POST /api/v1/payouts` with header `Idempotency-Key: <unique>` and body:

```json
{
  "merchantId":  "...",
  "asset":       "eth:mainnet:usdc",
  "fromAddress": "0x...",
  "toAddress":   "0x...",
  "amount":      500000
}
```

Merchants receive `X-Cpayment-Signature` HMAC-SHA256-signed webhooks for
invoice and payout lifecycle events (see `PayoutWebhookPayload` /
`WebhookPayload` for the wire shape).

## Repository

`origin/main` on github.com/ismetaba/cpayment
