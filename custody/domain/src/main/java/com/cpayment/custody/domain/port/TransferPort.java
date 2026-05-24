package com.cpayment.custody.domain.port;

import com.cpayment.custody.domain.model.FeeBump;
import com.cpayment.custody.domain.model.Page;
import com.cpayment.custody.domain.model.SendTransferCommand;
import com.cpayment.custody.domain.model.Transfer;
import com.cpayment.custody.domain.model.TransferFilter;
import com.cpayment.custody.domain.model.TransferId;

import java.util.Optional;

public interface TransferPort {

    /**
     * Submit a transfer. Returns the custody-side TransferId immediately.
     * Note: a successful return does NOT mean the tx is broadcast — see TransferState.
     * Idempotency: same idempotencyKey returns the existing TransferId without resubmitting.
     */
    TransferId sendTransfer(SendTransferCommand cmd);

    TransferId speedUp(TransferId original, FeeBump bump);

    Optional<Transfer> findTransfer(TransferId id);

    Page<Transfer> listTransfers(TransferFilter filter);
}
