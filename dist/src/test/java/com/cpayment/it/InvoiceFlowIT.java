package com.cpayment.it;

import com.cpayment.CpaymentApplication;
import com.cpayment.custody.infra.cusserver.event.dto.CreateDepositTransactionsEventDTO;
import com.cpayment.custody.infra.cusserver.event.dto.DepositTransactionDTO;
import com.cpayment.payment.domain.model.Invoice;
import com.cpayment.payment.domain.model.InvoiceId;
import com.cpayment.payment.domain.model.InvoiceStatus;
import com.cpayment.payment.domain.port.InvoiceRepository;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigInteger;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Full Spring Boot context exercising the deposit-address-then-detection slice.
 *
 * <p>Real components: RabbitMQ (Testcontainers), embedded HTTP server, all Spring beans.
 * Stubbed externals: Keycloak token endpoint and cus-server REST via WireMock.
 *
 * <p>Asserts the production-hardened paths:
 * <ol>
 *   <li>POST /invoices → 201, Location header, deposit address echoed from "cus-server".</li>
 *   <li>Same Idempotency-Key replay → cached invoice, no second cus-server call (verified
 *       via WireMock request count).</li>
 *   <li>Deposit event on RabbitMQ → invoice transitions PAID asynchronously.</li>
 * </ol>
 */
@SpringBootTest(
    classes = CpaymentApplication.class,
    webEnvironment = WebEnvironment.RANDOM_PORT
)
@ActiveProfiles("it")
@Import(RabbitTestConfig.class)
@Testcontainers
class InvoiceFlowIT {

    @Container
    static final RabbitMQContainer RABBIT = new RabbitMQContainer("rabbitmq:3.13-management");

    static WireMockServer wireMock;

    @BeforeAll
    static void startWireMock() {
        wireMock = new WireMockServer(WireMockConfiguration.options().dynamicPort());
        wireMock.start();
    }

    @AfterAll
    static void stopWireMock() {
        if (wireMock != null) wireMock.stop();
    }

    @DynamicPropertySource
    static void overrides(DynamicPropertyRegistry registry) {
        registry.add("spring.rabbitmq.host", RABBIT::getHost);
        registry.add("spring.rabbitmq.port", RABBIT::getAmqpPort);
        registry.add("spring.rabbitmq.username", RABBIT::getAdminUsername);
        registry.add("spring.rabbitmq.password", RABBIT::getAdminPassword);
        registry.add("cpayment.custody.cusserver.base-url", wireMock::baseUrl);
        registry.add("cpayment.custody.cusserver.holder-jwt-issuer-url", wireMock::baseUrl);
    }

    @Autowired TestRestTemplate http;
    @Autowired RabbitTemplate rabbit;
    @Autowired InvoiceRepository invoices;

    private static final String MERCHANT = "11111111-1111-1111-1111-111111111111";

    @Test
    void create_invoice_then_detect_deposit_marks_invoice_paid() {
        stubTokenEndpoint();
        UUID custodyAccountId = UUID.randomUUID();
        stubCreateAccount(custodyAccountId, "0xCAFEBABEINTEGRATION");

        // 1. POST /invoices with an Idempotency-Key
        String idempotencyKey = "merchant-order-" + UUID.randomUUID();
        ResponseEntity<InvoiceJson> created = postInvoice(idempotencyKey);
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(created.getHeaders().getLocation()).isNotNull();
        InvoiceJson body = created.getBody();
        assertThat(body).isNotNull();
        assertThat(body.status).isEqualTo("AWAITING_DEPOSIT");
        assertThat(body.depositAddress).isEqualTo("0xCAFEBABEINTEGRATION");
        wireMock.verify(1, postRequestedFor(urlEqualTo("/api/v1/holder/accounts")));

        // 2. Same key + body → idempotent, no second cus-server call
        ResponseEntity<InvoiceJson> replay = postInvoice(idempotencyKey);
        assertThat(replay.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(replay.getBody().id).isEqualTo(body.id);
        wireMock.verify(1, postRequestedFor(urlEqualTo("/api/v1/holder/accounts")));

        // 3. Publish a deposit event for the just-created custody account
        rabbit.convertAndSend("cpayment.test.deposits",
            new CreateDepositTransactionsEventDTO(List.of(new DepositTransactionDTO(
                UUID.randomUUID(),
                custodyAccountId,
                "0xSENDER",
                "ETHEREUM",
                "USDC",
                BigInteger.valueOf(1_000_000L),
                "0xdeadbeefdeadbeef",
                15,                              // >= default ETH minConfirmations (12)
                Instant.parse("2026-05-25T12:00:00Z")
            ))));

        // 4. Wait for the listener to mark PAID
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            Optional<Invoice> invoice = invoices.findById(InvoiceId.of(UUID.fromString(body.id)));
            assertThat(invoice).isPresent();
            assertThat(invoice.get().status()).isEqualTo(InvoiceStatus.PAID);
        });
    }

    // ---------------- helpers ----------------

    private ResponseEntity<InvoiceJson> postInvoice(String idempotencyKey) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.add("Idempotency-Key", idempotencyKey);
        String body = """
            { "merchantId": "%s", "asset": "eth:mainnet:usdc", "expectedAmount": 1000000 }
            """.formatted(MERCHANT);
        return http.exchange("/api/v1/invoices", HttpMethod.POST,
            new HttpEntity<>(body, headers), InvoiceJson.class);
    }

    private void stubTokenEndpoint() {
        wireMock.stubFor(post(urlEqualTo("/protocol/openid-connect/token"))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("""
                    { "access_token": "test-token", "expires_in": 3600, "token_type": "Bearer" }
                    """)));
    }

    private void stubCreateAccount(UUID accountId, String address) {
        wireMock.stubFor(post(urlEqualTo("/api/v1/holder/accounts"))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("""
                    {
                      "data": {
                        "id": "%s",
                        "label": "invoice-test",
                        "networkName": "ETHEREUM",
                        "address": "%s",
                        "supportedAssets": ["USDC"]
                      }
                    }
                    """.formatted(accountId, address))));
    }

    /**
     * Mirror of {@code InvoiceResponse} kept loose (Strings) so a slight serialization
     * change to the controller doesn't break this contract test.
     */
    record InvoiceJson(String id, String merchantId, String asset, String expectedAmount,
                       String status, String depositAddress, String createdAt) {}
}
