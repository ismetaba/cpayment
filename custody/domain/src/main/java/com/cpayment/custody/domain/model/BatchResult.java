package com.cpayment.custody.domain.model;

import java.util.List;

public record BatchResult(BatchSemantic semantic, List<TransferOutcome> outcomes) {}
