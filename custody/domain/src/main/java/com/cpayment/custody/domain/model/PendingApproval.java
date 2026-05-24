package com.cpayment.custody.domain.model;

import java.time.Instant;

public record PendingApproval(TransferId transferId, String reason, Instant createdAt) {}
