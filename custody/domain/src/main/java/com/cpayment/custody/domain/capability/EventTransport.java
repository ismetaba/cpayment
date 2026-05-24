package com.cpayment.custody.domain.capability;

public enum EventTransport {
    /** Adapter consumes provider's internal bus (e.g. cus-server RabbitMQ) and re-emits CustodyEvents. */
    BRIDGE,
    /** Provider posts HTTPS webhooks to a public endpoint. */
    WEBHOOK,
    /** No push; adapter polls provider REST periodically. */
    POLL
}
