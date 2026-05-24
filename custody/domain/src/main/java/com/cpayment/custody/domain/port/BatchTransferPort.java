package com.cpayment.custody.domain.port;

import com.cpayment.custody.domain.model.BatchResult;
import com.cpayment.custody.domain.model.SendTransferCommand;

import java.util.List;

/**
 * Optional capability. Implementations expose their semantic via Capabilities.batch(),
 * so callers know whether failure of one item rolls back the rest.
 */
public interface BatchTransferPort {
    BatchResult sendBatch(List<SendTransferCommand> commands);
}
