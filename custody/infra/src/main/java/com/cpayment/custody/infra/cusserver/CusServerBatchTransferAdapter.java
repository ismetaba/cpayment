package com.cpayment.custody.infra.cusserver;

import com.cpayment.custody.domain.model.BatchResult;
import com.cpayment.custody.domain.model.BatchSemantic;
import com.cpayment.custody.domain.model.SendTransferCommand;
import com.cpayment.custody.domain.model.TransferOutcome;
import com.cpayment.custody.domain.port.BatchTransferPort;
import com.cpayment.custody.domain.port.TransferPort;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Best-effort batch: loops single sendTransfer calls. Failures are surfaced per-item
 * via TransferOutcome.failureReason; the caller decides how to react.
 */
@Component
public class CusServerBatchTransferAdapter implements BatchTransferPort {

    private final TransferPort transferPort;

    public CusServerBatchTransferAdapter(TransferPort transferPort) {
        this.transferPort = transferPort;
    }

    @Override
    public BatchResult sendBatch(List<SendTransferCommand> commands) {
        List<TransferOutcome> outcomes = new ArrayList<>(commands.size());
        for (SendTransferCommand cmd : commands) {
            try {
                var id = transferPort.sendTransfer(cmd);
                outcomes.add(new TransferOutcome(cmd.idempotencyKey(), Optional.of(id), true, Optional.empty()));
            } catch (RuntimeException ex) {
                outcomes.add(new TransferOutcome(cmd.idempotencyKey(), Optional.empty(), false,
                                                 Optional.of(ex.getMessage())));
            }
        }
        return new BatchResult(BatchSemantic.BEST_EFFORT, outcomes);
    }
}
